package com.projeto.mapi.repository.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.SourceRunSummary;
import com.projeto.mapi.service.ingestion.RunCounters;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência de ingestion_run (JDBC puro: tabelas do pipeline não têm entidade JPA). */
@Repository
@RequiredArgsConstructor
public class IngestionRunRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public void create(UUID id, String source, String kind, String mode, Instant from, Instant to,
                       String codeVersion, String parserVersion, String ruleVersion, Instant startedAt,
                       UUID parentRunId, JsonNode params) {
        jdbc.update("""
                INSERT INTO ingestion_run (id, source, kind, mode, params, interval_start, interval_end, code_version,
                  parser_version, rule_version, started_at, status, parent_run_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?)
                """,
                ps -> {
                    ps.setObject(1, id);
                    ps.setString(2, source);
                    ps.setString(3, kind);
                    ps.setString(4, mode);
                    ps.setObject(5, json(params), Types.OTHER);
                    ps.setObject(6, odt(from));
                    ps.setObject(7, odt(to));
                    ps.setString(8, codeVersion);
                    ps.setString(9, parserVersion);
                    ps.setString(10, ruleVersion);
                    ps.setObject(11, odt(startedAt));
                    ps.setObject(12, parentRunId);
                });
    }

    public void finish(UUID id, String status, Instant finishedAt, RunCounters c, String errorSummary, JsonNode params) {
        jdbc.update("""
                UPDATE ingestion_run SET status = ?, finished_at = ?, error_summary = ?, params = ?,
                  fetched = ?, batch_deduped = ?, raw_inserted = ?, raw_unchanged = ?, valid = ?, quarantined = ?,
                  gaps = ?, obs_inserted = ?, obs_updated = ?, obs_unchanged = ?, obs_retracted = ?, absent_in_refetch = ?
                WHERE id = ?
                """,
                ps -> {
                    ps.setString(1, status);
                    ps.setObject(2, odt(finishedAt));
                    ps.setString(3, errorSummary);
                    ps.setObject(4, json(params), Types.OTHER);
                    ps.setInt(5, c.getFetched());
                    ps.setInt(6, c.getBatchDeduped());
                    ps.setInt(7, c.getRawInserted());
                    ps.setInt(8, c.getRawUnchanged());
                    ps.setInt(9, c.getValid());
                    ps.setInt(10, c.getQuarantined());
                    ps.setInt(11, c.getGaps());
                    ps.setInt(12, c.getObsInserted());
                    ps.setInt(13, c.getObsUpdated());
                    ps.setInt(14, c.getObsUnchanged());
                    ps.setInt(15, c.getObsRetracted());
                    ps.setInt(16, c.getAbsentInRefetch());
                    ps.setObject(17, id);
                });
    }

    /** Último run OK/PARTIAL da fonte que cobre o intervalo (pai do reprocessamento). */
    public Optional<UUID> lastSuccessfulRun(String source, Instant from, Instant to) {
        List<UUID> ids = jdbc.query("""
                SELECT id FROM ingestion_run
                WHERE source = ? AND status IN ('OK', 'PARTIAL')
                  AND (interval_start IS NULL OR interval_start < ?) AND (interval_end IS NULL OR interval_end > ?)
                ORDER BY started_at DESC LIMIT 1
                """, (rs, i) -> rs.getObject(1, UUID.class), source, odt(to), odt(from));
        return ids.stream().findFirst();
    }

    public Optional<IngestionRunView> findById(UUID id) {
        return jdbc.query("SELECT * FROM ingestion_run WHERE id = ?", (rs, i) -> map(rs), id).stream().findFirst();
    }

    /** Página de runs, mais recentes primeiro; {@code source} e {@code status} opcionais. */
    public List<IngestionRunView> page(String source, String status, int limit, long offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ingestion_run WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        appendFilters(sql, args, source, status);
        sql.append(" ORDER BY started_at DESC, id LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, i) -> map(rs), args.toArray());
    }

    public long count(String source, String status) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM ingestion_run WHERE 1=1");
        List<Object> args = new java.util.ArrayList<>();
        appendFilters(sql, args, source, status);
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    private static void appendFilters(StringBuilder sql, List<Object> args, String source, String status) {
        if (source != null && !source.isBlank()) {
            sql.append(" AND source = ?");
            args.add(source);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
    }

    /** Um resumo por fonte (do source_registry e de qualquer fonte que já teve run). */
    public List<SourceRunSummary> sourceSummaries(Instant since24h) {
        return jdbc.query("""
                SELECT sr.source,
                       l.id AS last_run_id, l.status AS last_status, l.error_summary AS last_error,
                       l.valid AS last_valid, l.quarantined AS last_quarantined,
                       s.last_success_at,
                       COALESCE(a.rows24, 0) AS rows24
                FROM (SELECT source FROM source_registry UNION SELECT DISTINCT source FROM ingestion_run) sr
                LEFT JOIN LATERAL (SELECT * FROM ingestion_run x WHERE x.source = sr.source
                                   ORDER BY x.started_at DESC LIMIT 1) l ON TRUE
                LEFT JOIN LATERAL (SELECT max(x.finished_at) AS last_success_at FROM ingestion_run x
                                   WHERE x.source = sr.source AND x.status IN ('OK', 'PARTIAL')) s ON TRUE
                LEFT JOIN LATERAL (SELECT sum(x.obs_inserted + x.obs_updated) AS rows24 FROM ingestion_run x
                                   WHERE x.source = sr.source AND x.started_at >= ?) a ON TRUE
                ORDER BY sr.source
                """, (rs, i) -> new SourceRunSummary(rs.getString("source"), rs.getObject("last_run_id", UUID.class),
                rs.getString("last_status"), rs.getString("last_error"), rs.getObject("last_valid", Integer.class),
                rs.getObject("last_quarantined", Integer.class), inst(rs, "last_success_at"), rs.getLong("rows24")),
                odt(since24h));
    }

    /** Fecha o lock da fonte na transação corrente (um lote por fonte por vez; ADR 0006 seção 1). */
    public void lockSource(String source) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { }, source);
    }

    private IngestionRunView map(ResultSet rs) throws SQLException {
        return new IngestionRunView(
                rs.getObject("id", UUID.class), rs.getString("source"), rs.getString("kind"), rs.getString("mode"),
                rs.getString("status"), inst(rs, "interval_start"), inst(rs, "interval_end"),
                inst(rs, "started_at"), inst(rs, "finished_at"), rs.getString("code_version"),
                rs.getString("parser_version"), rs.getString("rule_version"), rs.getObject("parent_run_id", UUID.class),
                rs.getString("error_summary"), rs.getInt("fetched"), rs.getInt("batch_deduped"),
                rs.getInt("raw_inserted"), rs.getInt("raw_unchanged"), rs.getInt("valid"), rs.getInt("quarantined"),
                rs.getInt("gaps"), rs.getInt("obs_inserted"), rs.getInt("obs_updated"), rs.getInt("obs_unchanged"),
                rs.getInt("obs_retracted"), rs.getInt("absent_in_refetch"), parse(rs.getString("params")));
    }

    private static Instant inst(ResultSet rs, String col) throws SQLException {
        OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }

    private static OffsetDateTime odt(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private String json(JsonNode n) {
        try {
            return mapper.writeValueAsString(n == null ? mapper.createObjectNode() : n);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode parse(String s) {
        try {
            return s == null ? null : mapper.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

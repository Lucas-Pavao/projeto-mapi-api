package com.projeto.mapi.repository.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.ingestion.OriginKey;
import com.projeto.mapi.service.ingestion.RawRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staging bruto append-only (ADR 0006 seção 1). Só INSERT e SELECT. */
@Repository
@RequiredArgsConstructor
public class RawObservationRepository {

    /** Versão gravada de um registro de origem. */
    public record RawVersion(UUID id, Instant ingestedAt, String contentHash, RawRecord record) {}

    /** Linha nova a gravar. */
    public record RawInsert(UUID id, RawRecord record, String contentHash) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** Versão MAIS RECENTE (maior ingested_at, depois seq) de cada registro de origem pedido. */
    public Map<OriginKey, RawVersion> latestVersions(String source, Collection<OriginKey> keys) {
        Map<OriginKey, RawVersion> result = new HashMap<>();
        if (keys.isEmpty()) return result;
        String[] stations = keys.stream().map(OriginKey::stationId).distinct().toArray(String[]::new);
        String[] norms = keys.stream().map(OriginKey::sourceTsNorm).distinct().toArray(String[]::new);
        jdbc.query(con -> {
            var ps = con.prepareStatement("""
                    SELECT DISTINCT ON (station_id, source_ts_norm) id, ingested_at, content_hash, station_id,
                           collected_at, source_ts_text, source_ts_norm, source_tz, payload::text AS payload
                    FROM raw_observation
                    WHERE source = ? AND station_id = ANY(?) AND source_ts_norm = ANY(?)
                    ORDER BY station_id, source_ts_norm, ingested_at DESC, seq DESC
                    """);
            ps.setString(1, source);
            ps.setArray(2, con.createArrayOf("text", stations));
            ps.setArray(3, con.createArrayOf("text", norms));
            return ps;
        }, rs -> {
            RawVersion v = map(source, rs);
            if (keys.contains(v.record().key())) result.put(v.record().key(), v);
        });
        return result;
    }

    /** Versão mais recente de cada registro de origem com source_ts_norm em [lo, hi) (comparação textual ISO). */
    public List<RawVersion> latestInTextWindow(String source, String loInclusive, String hiExclusive) {
        return jdbc.query("""
                SELECT DISTINCT ON (station_id, source_ts_norm) id, ingested_at, content_hash, station_id,
                       collected_at, source_ts_text, source_ts_norm, source_tz, payload::text AS payload
                FROM raw_observation
                WHERE source = ? AND source_ts_norm >= ? AND source_ts_norm < ?
                ORDER BY station_id, source_ts_norm, ingested_at DESC, seq DESC
                """, (rs, i) -> map(source, rs), source, loInclusive, hiExclusive);
    }

    /**
     * Versão mais recente de cada registro de origem de um recurso (station_id começa com {@code prefix}),
     * em ordem de station_id, a partir de {@code afterStation} (exclusivo). Paginação por chave para o reprocessamento
     * do bruto sem carregar o recurso inteiro.
     */
    public List<RawVersion> latestByStationPrefix(String source, String prefix, String afterStation, int limit) {
        return jdbc.query("""
                SELECT DISTINCT ON (station_id) id, ingested_at, content_hash, station_id,
                       collected_at, source_ts_text, source_ts_norm, source_tz, payload::text AS payload
                FROM raw_observation
                WHERE source = ? AND station_id LIKE ? AND station_id > ?
                ORDER BY station_id, ingested_at DESC, seq DESC
                LIMIT ?
                """, (rs, i) -> map(source, rs), source, prefix.replace("%", "\\%").replace("_", "\\_") + "%",
                afterStation, limit);
    }

    /** Payload da versão mais recente de um registro de origem pela station_id (sem olhar o instante de origem). */
    public java.util.Optional<com.fasterxml.jackson.databind.JsonNode> latestPayload(String source, String stationId) {
        return jdbc.query("""
                SELECT payload::text FROM raw_observation WHERE source = ? AND station_id = ?
                ORDER BY ingested_at DESC, seq DESC LIMIT 1
                """, (rs, i) -> {
            try {
                return mapper.readTree(rs.getString(1));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }, source, stationId).stream().findFirst();
    }

    /** run_id da versão mais recente do bruto de um registro de origem (proveniência de leituras ao vivo). */
    public java.util.Optional<UUID> latestRunId(String source, String stationId, String sourceTsNorm) {
        return jdbc.query("""
                SELECT run_id FROM raw_observation
                WHERE source = ? AND station_id = ? AND source_ts_norm = ?
                ORDER BY ingested_at DESC, seq DESC LIMIT 1
                """, (rs, i) -> rs.getObject(1, UUID.class), source, stationId, sourceTsNorm).stream().findFirst();
    }

    public void insertAll(UUID runId, Instant ingestedAt, List<RawInsert> rows) {
        if (rows.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO raw_observation (id, ingested_at, run_id, source, station_id, collected_at,
                  source_ts_text, source_ts_norm, source_tz, payload, content_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, 500, (ps, r) -> {
            RawRecord rec = r.record();
            ps.setObject(1, r.id());
            ps.setObject(2, OffsetDateTime.ofInstant(ingestedAt, ZoneOffset.UTC));
            ps.setObject(3, runId);
            ps.setString(4, rec.source());
            ps.setString(5, rec.stationId());
            ps.setObject(6, OffsetDateTime.ofInstant(rec.collectedAt(), ZoneOffset.UTC));
            ps.setString(7, rec.sourceTsText());
            ps.setString(8, rec.sourceTsNorm());
            ps.setString(9, rec.sourceTz());
            ps.setObject(10, rec.payload().toString(), Types.OTHER);
            ps.setString(11, r.contentHash());
        });
    }

    private RawVersion map(String source, java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            RawRecord rec = new RawRecord(source, rs.getString("station_id"),
                    rs.getObject("collected_at", OffsetDateTime.class).toInstant(),
                    rs.getString("source_ts_text"), rs.getString("source_ts_norm"), rs.getString("source_tz"),
                    mapper.readTree(rs.getString("payload")));
            return new RawVersion(rs.getObject("id", UUID.class),
                    rs.getObject("ingested_at", OffsetDateTime.class).toInstant(), rs.getString("content_hash"), rec);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

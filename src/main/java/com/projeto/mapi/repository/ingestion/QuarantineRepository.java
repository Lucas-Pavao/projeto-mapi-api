package com.projeto.mapi.repository.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Quarentena revisável (ADR 0006 seção 1). */
@Repository
@RequiredArgsConstructor
public class QuarantineRepository {

    /** Reprovação de uma versão do bruto. */
    public record Entry(UUID rawId, Instant rawIngestedAt, String ruleId, String reason, Map<String, Object> detail) {}

    /** Versão do bruto que passou a valer nesta execução, com as regras que ainda a reprovam. */
    public record Reviewed(java.util.UUID rawId, String source, String stationId, String sourceTsNorm,
                           List<String> activeRuleReasons) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** Insere as reprovações que ainda não estão abertas (mesmo raw, regra, motivo e variável). */
    public void insertIfAbsent(UUID runId, String ruleVersion, Instant now, List<Entry> entries) {
        if (entries.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO ingestion_quarantine (run_id, raw_id, raw_ingested_at, rule_id, rule_version, reason,
                  detail, quarantined_at)
                SELECT ?::uuid, ?::uuid, ?::timestamptz, ?::text, ?::text, ?::text, ?::jsonb, ?::timestamptz
                WHERE NOT EXISTS (SELECT 1 FROM ingestion_quarantine q
                  WHERE q.raw_id = ? AND q.rule_id = ? AND q.reason = ? AND q.resolved_at IS NULL
                    AND q.detail ->> 'variable' IS NOT DISTINCT FROM ?)
                """, entries, 500, (ps, e) -> {
            Object variable = e.detail().get("variable");
            ps.setObject(1, runId);
            ps.setObject(2, e.rawId());
            ps.setObject(3, OffsetDateTime.ofInstant(e.rawIngestedAt(), ZoneOffset.UTC));
            ps.setString(4, e.ruleId());
            ps.setString(5, ruleVersion);
            ps.setString(6, e.reason());
            ps.setObject(7, json(e.detail()), Types.OTHER);
            ps.setObject(8, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            ps.setObject(9, e.rawId());
            ps.setString(10, e.ruleId());
            ps.setString(11, e.reason());
            ps.setString(12, variable == null ? null : String.valueOf(variable));
        });
    }

    /**
     * Fecha as quarentenas abertas de cada registro de origem revisado que NÃO são mais reprovadas pela regra
     * vigente: reprovação em versão antiga do bruto, ou regra/motivo que deixou de se aplicar.
     */
    public void resolveStale(Instant now, String resolution, String source, List<Reviewed> reviewed) {
        if (reviewed.isEmpty()) return;
        jdbc.batchUpdate("""
                UPDATE ingestion_quarantine q SET resolved_at = ?, resolution = ?
                WHERE q.resolved_at IS NULL
                  AND q.raw_id IN (SELECT id FROM raw_observation WHERE source = ? AND station_id = ? AND source_ts_norm = ?)
                  AND NOT (q.raw_id = ? AND (q.rule_id || ':' || q.reason) = ANY(?))
                """, reviewed, 500, (ps, r) -> {
            ps.setObject(1, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            ps.setString(2, resolution);
            ps.setString(3, source);
            ps.setString(4, r.stationId());
            ps.setString(5, r.sourceTsNorm());
            ps.setObject(6, r.rawId());
            ps.setArray(7, ps.getConnection().createArrayOf("text", r.activeRuleReasons().toArray()));
        });
    }

    private String json(Map<String, Object> detail) {
        try {
            return mapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

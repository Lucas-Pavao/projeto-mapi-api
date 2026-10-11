package com.projeto.mapi.repository.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Série consolidada versionada (ADR 0006 seção 1): upsert em uma instrução por lote. */
@Repository
@RequiredArgsConstructor
public class ObservationRepository {

    /** Linha candidata, já deduplicada por chave natural. */
    public record ObsRow(String stationId, String variable, String resolution, Instant tsUtc, double value,
                         String unit, String qualifier, Instant intervalStartUtc, String qualityFlag,
                         String rawHash, String valueHash, String originTsNorm) {

        public String naturalKey() {
            return stationId + "|" + variable + "|" + resolution + "|" + tsUtc;
        }
    }

    private final JdbcTemplate jdbc;

    /**
     * INSERT ... ON CONFLICT DO UPDATE ... WHERE value_hash diferente OU status != ACTIVE. Valor igual e ACTIVE
     * não toca a linha (idempotência); RETRACTED com mesmo valor é reativada. Retorna {inseridas, atualizadas}.
     * O chamador deduplica o lote antes (o PostgreSQL falha se a mesma chave aparece duas vezes no comando).
     */
    public int[] upsert(UUID runId, String source, String ruleVersion, List<ObsRow> rows) {
        if (rows.isEmpty()) return new int[] {0, 0};
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        jdbc.query(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, qualifier,
                      interval_start_utc, quality_flag, revision, status, run_id, raw_hash, rule_version, value_hash,
                      origin_ts_norm)
                    SELECT ?, t.station_id, t.variable, t.resolution, t.ts_utc::timestamptz, t.value, t.unit,
                           t.qualifier, t.istart::timestamptz, t.flag, 1, 'ACTIVE', ?::uuid, t.raw_hash, ?,
                           t.value_hash, t.origin
                    FROM unnest(?::text[], ?::text[], ?::text[], ?::text[], ?::float8[], ?::text[], ?::text[],
                                ?::text[], ?::text[], ?::text[], ?::text[], ?::text[])
                         AS t(station_id, variable, resolution, ts_utc, value, unit, qualifier, istart, flag,
                              raw_hash, value_hash, origin)
                    ON CONFLICT (source, station_id, variable, resolution, ts_utc) DO UPDATE SET
                      value = EXCLUDED.value, unit = EXCLUDED.unit, qualifier = EXCLUDED.qualifier,
                      interval_start_utc = EXCLUDED.interval_start_utc, quality_flag = EXCLUDED.quality_flag,
                      status = 'ACTIVE', revision = observation.revision + 1, run_id = EXCLUDED.run_id,
                      raw_hash = EXCLUDED.raw_hash, rule_version = EXCLUDED.rule_version,
                      value_hash = EXCLUDED.value_hash, origin_ts_norm = EXCLUDED.origin_ts_norm
                    WHERE observation.value_hash IS DISTINCT FROM EXCLUDED.value_hash OR observation.status <> 'ACTIVE'
                    RETURNING (xmax = 0) AS inserted
                    """);
            ps.setString(1, source);
            ps.setString(2, runId.toString());
            ps.setString(3, ruleVersion);
            ps.setArray(4, con.createArrayOf("text", rows.stream().map(ObsRow::stationId).toArray()));
            ps.setArray(5, con.createArrayOf("text", rows.stream().map(ObsRow::variable).toArray()));
            ps.setArray(6, con.createArrayOf("text", rows.stream().map(ObsRow::resolution).toArray()));
            ps.setArray(7, con.createArrayOf("text", rows.stream().map(r -> r.tsUtc().toString()).toArray()));
            ps.setArray(8, con.createArrayOf("float8", rows.stream().map(ObsRow::value).toArray()));
            ps.setArray(9, con.createArrayOf("text", rows.stream().map(ObsRow::unit).toArray()));
            ps.setArray(10, con.createArrayOf("text", rows.stream().map(ObsRow::qualifier).toArray()));
            ps.setArray(11, con.createArrayOf("text", rows.stream().map(r -> r.intervalStartUtc().toString()).toArray()));
            ps.setArray(12, con.createArrayOf("text", rows.stream().map(ObsRow::qualityFlag).toArray()));
            ps.setArray(13, con.createArrayOf("text", rows.stream().map(ObsRow::rawHash).toArray()));
            ps.setArray(14, con.createArrayOf("text", rows.stream().map(ObsRow::valueHash).toArray()));
            ps.setArray(15, con.createArrayOf("text", rows.stream().map(ObsRow::originTsNorm).toArray()));
            return ps;
        }, rs -> {
            if (rs.getBoolean(1)) inserted.incrementAndGet();
            else updated.incrementAndGet();
        });
        return new int[] {inserted.get(), updated.get()};
    }

    /**
     * Retira (status RETRACTED, nova revisão) as linhas ACTIVE de registros de origem reprocessados cuja
     * chave natural NÃO está entre as mantidas. Nada é apagado; o gatilho copia a linha antiga para
     * observation_revision. Retorna quantas linhas foram retiradas.
     */
    public int retractNotKept(UUID runId, String source, List<String> originStations, List<String> originNorms,
                              List<ObsRow> kept) {
        if (originStations.isEmpty()) return 0;
        return jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    UPDATE observation o SET status = 'RETRACTED', revision = o.revision + 1, run_id = ?::uuid
                    FROM unnest(?::text[], ?::text[]) AS org(station_id, norm)
                    WHERE o.source = ? AND o.station_id = org.station_id AND o.origin_ts_norm = org.norm
                      AND o.status = 'ACTIVE'
                      AND NOT EXISTS (
                        SELECT 1 FROM unnest(?::text[], ?::text[], ?::text[], ?::text[])
                             AS k(station_id, variable, resolution, ts)
                        WHERE k.station_id = o.station_id AND k.variable = o.variable
                          AND k.resolution = o.resolution AND k.ts::timestamptz = o.ts_utc)
                    """);
            ps.setString(1, runId.toString());
            ps.setArray(2, con.createArrayOf("text", originStations.toArray()));
            ps.setArray(3, con.createArrayOf("text", originNorms.toArray()));
            ps.setString(4, source);
            ps.setArray(5, con.createArrayOf("text", kept.stream().map(ObsRow::stationId).toArray()));
            ps.setArray(6, con.createArrayOf("text", kept.stream().map(ObsRow::variable).toArray()));
            ps.setArray(7, con.createArrayOf("text", kept.stream().map(ObsRow::resolution).toArray()));
            ps.setArray(8, con.createArrayOf("text", kept.stream().map(r -> r.tsUtc().toString()).toArray()));
            return ps;
        });
    }
}

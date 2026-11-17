package com.projeto.mapi.repository.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Idade da leitura aceita e cobertura à frente por fonte (V29). */
@Repository
@RequiredArgsConstructor
public class SourceFreshnessRepository {

    /** Janela de busca: fonte sem leitura aceita nesse período conta como sem dado (e a busca não varre 5 anos). */
    static final long LOOKBACK_DAYS = 60;
    static final long LOOKAHEAD_DAYS = 400;

    public record Limits(String source, Integer maxAgeMinutes, Integer minCoverageAheadMinutes) {}

    public record Readings(Instant lastAccepted, Instant lastAhead) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public List<Limits> limits() {
        return jdbc.query("SELECT source, max_age_minutes, min_coverage_ahead_minutes FROM source_registry ORDER BY source",
                (rs, i) -> new Limits(rs.getString(1), rs.getObject(2, Integer.class), rs.getObject(3, Integer.class)));
    }

    public Readings readings(String source, Instant now) {
        if ("INMET_AVISOS".equals(source)) {
            return jdbc.query("""
                    SELECT max(finished_at)
                    FROM ingestion_run
                    WHERE source = 'INMET_AVISOS' AND status IN ('OK', 'PARTIAL') AND quarantined = 0
                      AND finished_at >= ? AND finished_at <= ?
                    """, (rs, i) -> new Readings(inst(rs.getObject(1, OffsetDateTime.class)), null),
                    utc(now.minusSeconds(LOOKBACK_DAYS * 86400)), utc(now))
                    .stream().findFirst().orElse(new Readings(null, null));
        }
        return jdbc.query("""
                SELECT max(ts_utc) FILTER (WHERE ts_utc <= ?), max(ts_utc)
                FROM observation_current
                WHERE source = ? AND value IS NOT NULL AND ts_utc >= ? AND ts_utc <= ?
                """, (rs, i) -> new Readings(inst(rs.getObject(1, OffsetDateTime.class)), inst(rs.getObject(2, OffsetDateTime.class))),
                utc(now), source, utc(now.minusSeconds(LOOKBACK_DAYS * 86400)), utc(now.plusSeconds(LOOKAHEAD_DAYS * 86400)))
                .stream().findFirst().orElse(new Readings(null, null));
    }

    /** Já existe alerta de fonte parada para a fonte depois de {@code since}? (um alerta por episódio, lembrete diário) */
    public boolean alertedSince(String source, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM alert WHERE kind = 'SOURCE_STALE' AND station_id = ? "
                + "AND triggered_at >= ?", Integer.class, source, utc(since));
        return n != null && n > 0;
    }

    private static Instant inst(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }

    public Optional<Limits> limitsOf(String source) {
        return limits().stream().filter(l -> l.source().equals(source)).findFirst();
    }
}

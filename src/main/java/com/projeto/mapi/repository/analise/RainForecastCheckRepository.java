package com.projeto.mapi.repository.analise;

import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Result;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Leitura das previsões conferidas, quebras de série e gravação da conferência semanal (V43, V44). */
@Repository
@RequiredArgsConstructor
public class RainForecastCheckRepository {

    public record SeriesBreak(Instant breakAt, String model, String description) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /**
     * Previsões conferidas com chuva medida e emitidas em [from, to). {@code p60}: chuva do ponto na hora antes da emissão,
     * pela regra do treino (maior soma em (t − 60 min, t] entre as estações a até 5 km com leitura nos 70 min antes de t);
     * sem estação assim, nulo (vira a faixa "sem_dado", nunca "seco").
     */
    public List<Row> rows(Instant from, Instant to) {
        return jdbc.query("""
                SELECT r.issued_at, r.horizon_hours, r.q50_mm, r.q80_mm, r.q95_mm, r.observed_mm,
                       (SELECT max(s.mm) FROM (
                            SELECT (SELECT sum(b.value) FROM canonical_rain_block_v b
                                     WHERE b.canonical_id = n.canonical_id
                                       AND b.ts_utc > r.issued_at - interval '60 minutes' AND b.ts_utc <= r.issued_at) AS mm
                            FROM point_rain_neighbors_v n
                            WHERE n.flood_point_id = r.flood_point_id AND n.near
                              AND EXISTS (SELECT 1 FROM canonical_rain_block_v b WHERE b.canonical_id = n.canonical_id
                                            AND b.ts_utc > r.issued_at - interval '70 minutes' AND b.ts_utc <= r.issued_at)) s) AS p60
                FROM rain_forecast r
                WHERE r.verified_at IS NOT NULL AND r.observed_mm IS NOT NULL
                  AND r.issued_at >= ? AND r.issued_at < ?
                """, (rs, i) -> new Row(rs.getTimestamp(1).toInstant(), rs.getInt(2), rs.getDouble(3), rs.getDouble(4),
                rs.getDouble(5), rs.getDouble(6), rs.getObject(7) == null ? null : rs.getDouble(7)), utc(from), utc(to));
    }

    public Instant firstIssued() {
        Timestamp t = jdbc.queryForObject("SELECT min(issued_at) FROM rain_forecast", Timestamp.class);
        return t == null ? null : t.toInstant();
    }

    /** Quebras de série que afetam o rain_v1, em ordem. */
    public List<SeriesBreak> breaksAffecting(String what) {
        return jdbc.query("""
                SELECT break_at, model, description FROM source_series_break WHERE ? = ANY (affects) ORDER BY break_at
                """, (rs, i) -> new SeriesBreak(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3)), what);
    }

    public void upsert(String scope, Instant from, Instant to, String segment, Result r, String ruleVersion) {
        jdbc.update("""
                INSERT INTO rain_forecast_check (scope, period_from, period_to, segment, horizon_hours, n_forecasts, n_days,
                    n_rain_days, pinball_model, pinball_persistence, skill, skill_lo, skill_hi, test_skill, test_skill_lo,
                    test_skill_hi, status, rule_version, computed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (scope, period_from, period_to, segment, horizon_hours, rule_version) DO UPDATE SET
                    n_forecasts = EXCLUDED.n_forecasts, n_days = EXCLUDED.n_days, n_rain_days = EXCLUDED.n_rain_days,
                    pinball_model = EXCLUDED.pinball_model, pinball_persistence = EXCLUDED.pinball_persistence,
                    skill = EXCLUDED.skill, skill_lo = EXCLUDED.skill_lo, skill_hi = EXCLUDED.skill_hi,
                    status = EXCLUDED.status, computed_at = now()
                """, scope, utc(from), utc(to), segment, r.horizonHours(), r.nForecasts(), r.nDays(), r.nRainDays(),
                r.pinballModel(), r.pinballPersistence(), r.skill(), r.skillLo(), r.skillHi(), r.testSkill(), r.testSkillLo(),
                r.testSkillHi(), r.status(), ruleVersion);
    }

    public List<Map<String, Object>> latest(int limit) {
        return jdbc.queryForList("""
                SELECT scope, period_from, period_to, segment, horizon_hours, n_forecasts, n_days, n_rain_days, pinball_model,
                       pinball_persistence, skill, skill_lo, skill_hi, test_skill, test_skill_lo, test_skill_hi, status,
                       rule_version, computed_at
                FROM rain_forecast_check ORDER BY computed_at DESC, scope, horizon_hours LIMIT ?
                """, limit);
    }

    /** Já há pendência aberta para a chave nos últimos {@code days} dias? (uma por episódio) */
    public boolean alertedSince(String key, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM alert WHERE kind = 'MODEL_CHECK' AND station_id = ? AND triggered_at >= ?",
                Integer.class, key, utc(since));
        return n != null && n > 0;
    }
}

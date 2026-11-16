package com.projeto.mapi.repository.analise;

import com.projeto.mapi.dto.RainForecastAccuracyDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Leituras de vizinhança, previsão do Open-Meteo e previsões de chuva gravadas (V30). */
@Repository
@RequiredArgsConstructor
public class RainForecastRepository {

    public record Reading(String stationId, double lat, double lon, Instant ts, double mm) {}

    public record OmHour(Instant hourEndUtc, Double mm) {}

    public record Stored(long floodPointId, String slug, Instant issuedAt, int horizonHours, Instant validUntil, double q50,
                         double q80, double q95, double probMedia, double probForte, boolean forteAlert,
                         String modelVersion, String model, String sourcesJson, String missingJson,
                         Instant latestReadingUtc, Double latestReadingAgeMin) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    /** Blocos de chuva das estações a até 40 km do ponto, com fim em (from, to]. */
    public List<Reading> neighborReadings(long floodPointId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT n.canonical_id, n.lat, n.lon, b.ts_utc, b.value
                FROM point_rain_neighbors_v n
                JOIN canonical_rain_block_v b ON b.canonical_id = n.canonical_id
                WHERE n.flood_point_id = ? AND b.ts_utc > ? AND b.ts_utc <= ?
                ORDER BY b.ts_utc
                """, (rs, i) -> new Reading(rs.getString(1), rs.getDouble(2), rs.getDouble(3),
                instant(rs.getTimestamp(4)), rs.getDouble(5)), floodPointId, utc(from), utc(to));
    }

    public void upsertOmHour(long floodPointId, Instant hourEnd, Double mm, Double gridLat, Double gridLon, String rawHash) {
        jdbc.update("""
                INSERT INTO open_meteo_forecast_hour (flood_point_id, hour_end_utc, mm, issued_before_utc, grid_lat, grid_lon, raw_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (flood_point_id, hour_end_utc, product) DO NOTHING
                """, floodPointId, utc(hourEnd), mm, utc(hourEnd.minusSeconds(24 * 3600)), gridLat, gridLon, rawHash);
    }

    /** Horas previstas com fim em (from, to]; só o que foi emitido até {@code issuedAt}. */
    public List<OmHour> omHours(long floodPointId, Instant issuedAt, Instant from, Instant to) {
        return jdbc.query("""
                SELECT hour_end_utc, mm FROM open_meteo_forecast_hour
                WHERE flood_point_id = ? AND product = 'previous_day1' AND issued_before_utc <= ?
                  AND hour_end_utc > ? AND hour_end_utc <= ? ORDER BY hour_end_utc
                """, (rs, i) -> new OmHour(instant(rs.getTimestamp(1)), rs.getObject(2, Double.class)),
                floodPointId, utc(issuedAt), utc(from), utc(to));
    }

    public void insert(Stored s) {
        jdbc.update("""
                INSERT INTO rain_forecast (flood_point_id, issued_at, horizon_hours, valid_until, q50_mm, q80_mm, q95_mm,
                    prob_media, prob_forte, forte_alert, model_version, model, sources, missing_inputs,
                    latest_reading_utc, latest_reading_age_min)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                ON CONFLICT (flood_point_id, issued_at, horizon_hours) DO NOTHING
                """, s.floodPointId(), utc(s.issuedAt()), s.horizonHours(), utc(s.validUntil()), s.q50(), s.q80(), s.q95(),
                s.probMedia(), s.probForte(), s.forteAlert(), s.modelVersion(), s.model(), s.sourcesJson(), s.missingJson(),
                s.latestReadingUtc() == null ? null : utc(s.latestReadingUtc()), s.latestReadingAgeMin());
    }

    /** Última emissão de cada ponto (todas as linhas de horizonte dela). */
    public List<Stored> latest() {
        return jdbc.query("""
                SELECT r.flood_point_id, f.slug, r.issued_at, r.horizon_hours, r.valid_until, r.q50_mm, r.q80_mm, r.q95_mm,
                       r.prob_media, r.prob_forte, r.forte_alert, r.model_version, r.model, r.sources::text,
                       r.missing_inputs::text, r.latest_reading_utc, r.latest_reading_age_min
                FROM rain_forecast r JOIN flood_points f ON f.id = r.flood_point_id
                WHERE r.issued_at = (SELECT max(x.issued_at) FROM rain_forecast x WHERE x.flood_point_id = r.flood_point_id)
                ORDER BY r.flood_point_id, r.horizon_hours
                """, (rs, i) -> new Stored(rs.getLong(1), rs.getString(2), instant(rs.getTimestamp(3)), rs.getInt(4),
                instant(rs.getTimestamp(5)), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8), rs.getDouble(9),
                rs.getDouble(10), rs.getBoolean(11), rs.getString(12), rs.getString(13), rs.getString(14),
                rs.getString(15), instant(rs.getTimestamp(16)), rs.getObject(17, Double.class)));
    }

    /**
     * Confere as previsões vencidas há pelo menos {@code settle} (a PED chega com até ~1 h de atraso). Medida, pela regra
     * do treino: maior acumulado em (issued_at, valid_until] entre as estações a até 5 km que tinham leitura nos 70 min
     * antes do início E do fim da janela. Sem estação assim, a previsão fica marcada NO_MEASURE (não vira zero).
     */
    public int verifyPending(Instant now, long settleMinutes, double forteMmH) {
        return jdbc.update("""
                WITH pend AS (
                    SELECT r.id, r.flood_point_id, r.issued_at, r.valid_until
                    FROM rain_forecast r
                    WHERE r.verified_at IS NULL AND r.valid_until <= ?::timestamptz - make_interval(mins => ?::int)
                    LIMIT 20000
                ), st AS (
                    SELECT p.id, n.canonical_id,
                           (SELECT sum(b.value) FROM canonical_rain_block_v b
                             WHERE b.canonical_id = n.canonical_id AND b.ts_utc > p.issued_at AND b.ts_utc <= p.valid_until) AS mm,
                           (SELECT max(h.s) FROM (
                                SELECT sum(b2.value) OVER (ORDER BY b2.ts_utc RANGE BETWEEN interval '50 minutes' PRECEDING AND CURRENT ROW) AS s,
                                       b2.ts_utc
                                FROM canonical_rain_block_v b2
                                WHERE b2.canonical_id = n.canonical_id AND b2.ts_utc > p.issued_at AND b2.ts_utc <= p.valid_until) h
                             WHERE h.ts_utc >= p.issued_at + interval '60 minutes') AS peak
                    FROM pend p
                    JOIN point_rain_neighbors_v n ON n.flood_point_id = p.flood_point_id AND n.near
                    WHERE EXISTS (SELECT 1 FROM canonical_rain_block_v b WHERE b.canonical_id = n.canonical_id
                                    AND b.ts_utc > p.issued_at - interval '70 minutes' AND b.ts_utc <= p.issued_at)
                      AND EXISTS (SELECT 1 FROM canonical_rain_block_v b WHERE b.canonical_id = n.canonical_id
                                    AND b.ts_utc > p.valid_until - interval '70 minutes' AND b.ts_utc <= p.valid_until)
                ), agg AS (
                    SELECT p.id, max(coalesce(s.mm, 0)) FILTER (WHERE s.id IS NOT NULL) AS mm,
                           max(coalesce(s.peak, 0)) FILTER (WHERE s.id IS NOT NULL) AS peak
                    FROM pend p LEFT JOIN st s ON s.id = p.id GROUP BY p.id
                )
                UPDATE rain_forecast r
                   SET observed_mm = a.mm, observed_peak_mm_h = a.peak, verified_at = ?::timestamptz,
                       verification_note = CASE WHEN a.mm IS NULL THEN 'NO_MEASURE' ELSE 'MEASURED' END
                  FROM agg a WHERE a.id = r.id
                """, utc(now), settleMinutes, utc(now));
    }

    public List<RainForecastAccuracyDTO.Row> accuracy(Instant from, Instant to, double forteMmH) {
        return jdbc.query("""
                SELECT horizon_hours, count(*) AS n, count(*) FILTER (WHERE observed_mm IS NULL) AS no_measure,
                       avg((GREATEST(0.5 * (observed_mm - q50_mm), -0.5 * (observed_mm - q50_mm))
                          + GREATEST(0.8 * (observed_mm - q80_mm), -0.2 * (observed_mm - q80_mm))
                          + GREATEST(0.95 * (observed_mm - q95_mm), -0.05 * (observed_mm - q95_mm))) / 3.0) AS pinball,
                       avg((observed_mm <= q50_mm)::int)::float8 AS c50, avg((observed_mm <= q80_mm)::int)::float8 AS c80,
                       avg((observed_mm <= q95_mm)::int)::float8 AS c95, avg(observed_mm) AS mean_obs,
                       count(*) FILTER (WHERE observed_peak_mm_h >= ?) AS ev,
                       count(*) FILTER (WHERE forte_alert AND observed_mm IS NOT NULL) AS al,
                       count(*) FILTER (WHERE forte_alert AND observed_peak_mm_h >= ?) AS hit
                FROM rain_forecast
                WHERE verified_at IS NOT NULL AND issued_at > ? AND issued_at <= ?
                GROUP BY horizon_hours ORDER BY horizon_hours
                """, (rs, i) -> new RainForecastAccuracyDTO.Row(rs.getInt(1), rs.getLong(2), rs.getLong(3),
                rs.getObject(4, Double.class), rs.getObject(5, Double.class), rs.getObject(6, Double.class),
                rs.getObject(7, Double.class), rs.getObject(8, Double.class), rs.getLong(9), rs.getLong(10), rs.getLong(11)),
                forteMmH, forteMmH, utc(from), utc(to));
    }

    public long pending() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM rain_forecast WHERE verified_at IS NULL", Long.class);
        return n == null ? 0 : n;
    }

    public Instant lastVerifiedAt() {
        return instant(jdbc.queryForObject("SELECT max(verified_at) FROM rain_forecast", Timestamp.class));
    }
}

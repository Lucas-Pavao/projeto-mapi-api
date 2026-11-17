package com.projeto.mapi.repository.alerta;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência do alerta por ponto (V34). Nada é apagado: o estado é atualizado, ciclos e transições só crescem. */
@Repository
@RequiredArgsConstructor
public class AlertaRepository {

    public record State(long floodPointId, String slug, String name, String rainAlert, String rainReason, String pointFlood,
                        String floodReason, Instant rainSince, Instant lastRenewedAt, UUID eventId, Instant eventLastActiveAt,
                        String trigger, Double probForte1h, Double probOn, Double probOff, Double rainTeMm,
                        Double capacityTeMm, Double rain30minMm, Double hNowMm, Double hProjQ50MaxMm, Double hProjQ80MaxMm,
                        Integer floodInMinutes, String projectionJson, Double q50_1h, Double q80_1h, String rainModelVersion,
                        String bucketVersion, Double trYears, Double teMinutes, String sourcesJson, Instant latestReadingUtc,
                        Double latestReadingAgeMin, boolean shadow, UUID cycleId, Instant updatedAt) {}

    public record PointCycle(UUID cycleId, long floodPointId, Instant evaluatedAt, String rainAlert, String rainReason,
                             String pointFlood, String floodReason, Double probForte1h, Double rainTeMm, Double hNowMm,
                             Double hProjQ50MaxMm, Integer floodInMinutes, UUID eventId, Instant readingTs,
                             Instant readingReceivedAt, Instant readingIngestedAt, Instant cycleStartedAt,
                             Instant aiRespondedAt, Instant stateWrittenAt, Boolean wetHour, Double bucketMs, boolean shadow) {}

    public record Transition(long id, long floodPointId, String slug, Instant at, String field, String fromState,
                             String toState, String kind, String reason, UUID eventId, Double probForte1h, Double rainTeMm,
                             Double hNowMm, Double hProjQ50MaxMm, Integer floodInMinutes, boolean shadow, UUID cycleId) {}

    /** Mediana, p90 e máximo (s) de um trecho do caminho, separado por hora com e sem chuva. */
    public record LatencyStat(String segment, boolean wetHour, long n, Double medianS, Double p90S, Double maxS) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static Instant inst(ResultSet rs, String c) throws SQLException {
        Timestamp t = rs.getTimestamp(c);
        return t == null ? null : t.toInstant();
    }

    private static Double dbl(ResultSet rs, String c) throws SQLException {
        return rs.getObject(c, Double.class);
    }

    public void insertCycle(UUID id, String origin, Instant startedAt) {
        jdbc.update("INSERT INTO alert_cycle (id, origin, started_at) VALUES (?, ?, ?)", id, origin, utc(startedAt));
    }

    public void finishCycle(UUID id, Instant finishedAt, Instant sseSentAt, int nPoints, long durationMs, double bucketMs,
                            String error) {
        jdbc.update("UPDATE alert_cycle SET finished_at = ?, sse_sent_at = ?, n_points = ?, duration_ms = ?, bucket_ms_total = ?, "
                + "error = ? WHERE id = ?", utc(finishedAt), utc(sseSentAt), nPoints, (int) durationMs, bucketMs, error, id);
    }

    public Optional<State> state(long floodPointId) {
        return jdbc.query(STATE_SQL + " WHERE s.flood_point_id = ?", (rs, i) -> state(rs), floodPointId).stream().findFirst();
    }

    public List<State> states() {
        return jdbc.query(STATE_SQL + " WHERE f.active ORDER BY f.id", (rs, i) -> state(rs));
    }

    private static final String STATE_SQL = """
            SELECT s.*, f.slug, f.name FROM alert_point_state s JOIN flood_points f ON f.id = s.flood_point_id
            """;

    private static State state(ResultSet rs) throws SQLException {
        String ev = rs.getString("event_id");
        String cy = rs.getString("cycle_id");
        return new State(rs.getLong("flood_point_id"), rs.getString("slug"), rs.getString("name"), rs.getString("rain_alert"),
                rs.getString("rain_reason"), rs.getString("point_flood"), rs.getString("flood_reason"), inst(rs, "rain_since"),
                inst(rs, "last_renewed_at"), ev == null ? null : UUID.fromString(ev), inst(rs, "event_last_active_at"),
                rs.getString("trigger"), dbl(rs, "prob_forte_1h"), dbl(rs, "prob_on"), dbl(rs, "prob_off"),
                dbl(rs, "rain_te_mm"), dbl(rs, "capacity_te_mm"), dbl(rs, "rain_30min_mm"), dbl(rs, "h_now_mm"),
                dbl(rs, "h_proj_q50_max_mm"), dbl(rs, "h_proj_q80_max_mm"), rs.getObject("flood_in_minutes", Integer.class),
                rs.getString("projection"), dbl(rs, "q50_1h_mm"), dbl(rs, "q80_1h_mm"), rs.getString("rain_model_version"),
                rs.getString("bucket_version"), dbl(rs, "tr_years"), dbl(rs, "te_minutes"), rs.getString("sources"),
                inst(rs, "latest_reading_utc"), dbl(rs, "latest_reading_age_min"), rs.getBoolean("shadow"),
                cy == null ? null : UUID.fromString(cy), inst(rs, "updated_at"));
    }

    public void upsertState(State s) {
        jdbc.update("""
                INSERT INTO alert_point_state (flood_point_id, rain_alert, rain_reason, point_flood, flood_reason, rain_since,
                    last_renewed_at, event_id, event_last_active_at, trigger, prob_forte_1h, prob_on, prob_off, rain_te_mm,
                    capacity_te_mm, rain_30min_mm, h_now_mm, h_proj_q50_max_mm, h_proj_q80_max_mm, flood_in_minutes, projection,
                    q50_1h_mm, q80_1h_mm, rain_model_version, bucket_version, tr_years, te_minutes, sources, latest_reading_utc,
                    latest_reading_age_min, shadow, cycle_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                ON CONFLICT (flood_point_id) DO UPDATE SET
                    rain_alert = EXCLUDED.rain_alert, rain_reason = EXCLUDED.rain_reason, point_flood = EXCLUDED.point_flood,
                    flood_reason = EXCLUDED.flood_reason, rain_since = EXCLUDED.rain_since,
                    last_renewed_at = EXCLUDED.last_renewed_at, event_id = EXCLUDED.event_id,
                    event_last_active_at = EXCLUDED.event_last_active_at, trigger = EXCLUDED.trigger,
                    prob_forte_1h = EXCLUDED.prob_forte_1h, prob_on = EXCLUDED.prob_on, prob_off = EXCLUDED.prob_off,
                    rain_te_mm = EXCLUDED.rain_te_mm, capacity_te_mm = EXCLUDED.capacity_te_mm,
                    rain_30min_mm = EXCLUDED.rain_30min_mm, h_now_mm = EXCLUDED.h_now_mm,
                    h_proj_q50_max_mm = EXCLUDED.h_proj_q50_max_mm, h_proj_q80_max_mm = EXCLUDED.h_proj_q80_max_mm,
                    flood_in_minutes = EXCLUDED.flood_in_minutes, projection = EXCLUDED.projection,
                    q50_1h_mm = EXCLUDED.q50_1h_mm, q80_1h_mm = EXCLUDED.q80_1h_mm,
                    rain_model_version = EXCLUDED.rain_model_version, bucket_version = EXCLUDED.bucket_version,
                    tr_years = EXCLUDED.tr_years, te_minutes = EXCLUDED.te_minutes, sources = EXCLUDED.sources,
                    latest_reading_utc = EXCLUDED.latest_reading_utc, latest_reading_age_min = EXCLUDED.latest_reading_age_min,
                    shadow = EXCLUDED.shadow, cycle_id = EXCLUDED.cycle_id, updated_at = EXCLUDED.updated_at
                """, s.floodPointId(), s.rainAlert(), s.rainReason(), s.pointFlood(), s.floodReason(), utc(s.rainSince()),
                utc(s.lastRenewedAt()), s.eventId(), utc(s.eventLastActiveAt()), s.trigger(), s.probForte1h(), s.probOn(),
                s.probOff(), s.rainTeMm(), s.capacityTeMm(), s.rain30minMm(), s.hNowMm(), s.hProjQ50MaxMm(), s.hProjQ80MaxMm(),
                s.floodInMinutes(), s.projectionJson(), s.q50_1h(), s.q80_1h(), s.rainModelVersion(), s.bucketVersion(),
                s.trYears(), s.teMinutes(), s.sourcesJson(), utc(s.latestReadingUtc()), s.latestReadingAgeMin(), s.shadow(),
                s.cycleId(), utc(s.updatedAt()));
    }

    public void insertPointCycle(PointCycle c) {
        jdbc.update("""
                INSERT INTO alert_point_cycle (cycle_id, flood_point_id, evaluated_at, rain_alert, rain_reason, point_flood,
                    flood_reason, prob_forte_1h, rain_te_mm, h_now_mm, h_proj_q50_max_mm, flood_in_minutes, event_id, reading_ts,
                    reading_received_at, reading_ingested_at, cycle_started_at, ai_responded_at, state_written_at, wet_hour,
                    bucket_ms, shadow)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, c.cycleId(), c.floodPointId(), utc(c.evaluatedAt()), c.rainAlert(), c.rainReason(), c.pointFlood(),
                c.floodReason(), c.probForte1h(), c.rainTeMm(), c.hNowMm(), c.hProjQ50MaxMm(), c.floodInMinutes(), c.eventId(),
                utc(c.readingTs()), utc(c.readingReceivedAt()), utc(c.readingIngestedAt()), utc(c.cycleStartedAt()),
                utc(c.aiRespondedAt()), utc(c.stateWrittenAt()), c.wetHour(), c.bucketMs(), c.shadow());
    }

    public void markSseSent(UUID cycleId, long floodPointId, Instant at) {
        jdbc.update("UPDATE alert_point_cycle SET sse_sent_at = ? WHERE cycle_id = ? AND flood_point_id = ?", utc(at), cycleId,
                floodPointId);
    }

    public void insertTransition(long floodPointId, Instant at, String field, String from, String to, String kind, String reason,
                                 UUID eventId, Double prob, Double rainTe, Double hNow, Double hProj, Integer floodIn,
                                 boolean shadow, UUID cycleId) {
        jdbc.update("""
                INSERT INTO alert_transition (flood_point_id, at, field, from_state, to_state, kind, reason, event_id,
                    prob_forte_1h, rain_te_mm, h_now_mm, h_proj_q50_max_mm, flood_in_minutes, shadow, cycle_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, floodPointId, utc(at), field, from, to, kind, reason, eventId, prob, rainTe, hNow, hProj, floodIn, shadow,
                cycleId);
    }

    public List<Transition> transitions(Long floodPointId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                SELECT t.*, f.slug FROM alert_transition t JOIN flood_points f ON f.id = t.flood_point_id
                WHERE (?::bigint IS NULL OR t.flood_point_id = ?) AND t.at >= ? AND t.at <= ?
                ORDER BY t.at DESC, t.id DESC LIMIT ?
                """, (rs, i) -> {
            String ev = rs.getString("event_id");
            String cy = rs.getString("cycle_id");
            return new Transition(rs.getLong("id"), rs.getLong("flood_point_id"), rs.getString("slug"), inst(rs, "at"),
                    rs.getString("field"), rs.getString("from_state"), rs.getString("to_state"), rs.getString("kind"),
                    rs.getString("reason"), ev == null ? null : UUID.fromString(ev), dbl(rs, "prob_forte_1h"),
                    dbl(rs, "rain_te_mm"), dbl(rs, "h_now_mm"), dbl(rs, "h_proj_q50_max_mm"),
                    rs.getObject("flood_in_minutes", Integer.class), rs.getBoolean("shadow"), cy == null ? null : UUID.fromString(cy));
        }, floodPointId, floodPointId, utc(from), utc(to), limit);
    }

    /**
     * Atraso por trecho (item 2.1), por leitura, no primeiro ciclo que a usou: estação→coleta, coleta→banco, banco→ciclo, ciclo→IA,
     * IA→estado, estado→SSE e o total estação→SSE. Só ciclos com SSE enviado e leitura com instante de recebimento.
     */
    public List<LatencyStat> latency(Instant from, Instant to) {
        return jdbc.query("""
                WITH f AS (
                    -- cada leitura conta uma vez: o primeiro ciclo que a usou (os seguintes só a repetem)
                    SELECT DISTINCT ON (flood_point_id, reading_ts) *
                    FROM alert_point_cycle
                    WHERE evaluated_at >= ? AND evaluated_at <= ? AND reading_received_at IS NOT NULL AND sse_sent_at IS NOT NULL
                    ORDER BY flood_point_id, reading_ts, evaluated_at
                ), c AS (
                    SELECT coalesce(wet_hour, false) AS wet,
                           extract(epoch FROM reading_received_at - reading_ts) AS s1,
                           extract(epoch FROM reading_ingested_at - reading_received_at) AS s2,
                           extract(epoch FROM cycle_started_at - reading_ingested_at) AS s3,
                           extract(epoch FROM ai_responded_at - cycle_started_at) AS s4,
                           extract(epoch FROM state_written_at - ai_responded_at) AS s5,
                           extract(epoch FROM sse_sent_at - state_written_at) AS s6,
                           extract(epoch FROM sse_sent_at - reading_ts) AS total
                    FROM f
                ), u AS (
                    SELECT wet, 'estacao_coleta' seg, s1 v FROM c UNION ALL SELECT wet, 'coleta_banco', s2 FROM c
                    UNION ALL SELECT wet, 'banco_ciclo', s3 FROM c UNION ALL SELECT wet, 'ciclo_ia', s4 FROM c
                    UNION ALL SELECT wet, 'ia_estado', s5 FROM c UNION ALL SELECT wet, 'estado_sse', s6 FROM c
                    UNION ALL SELECT wet, 'total', total FROM c
                )
                SELECT seg, wet, count(v), percentile_cont(0.5) WITHIN GROUP (ORDER BY v)::float8,
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY v)::float8, max(v)::float8
                FROM u WHERE v IS NOT NULL GROUP BY seg, wet ORDER BY seg, wet
                """, (rs, i) -> new LatencyStat(rs.getString(1), rs.getBoolean(2), rs.getLong(3), rs.getObject(4, Double.class),
                rs.getObject(5, Double.class), rs.getObject(6, Double.class)), utc(from), utc(to));
    }

    public void insertQcDrop(String canonicalId, Instant ts, double mm, String reason, Instant seenAt) {
        jdbc.update("INSERT INTO live_qc_drop (canonical_id, ts_utc, mm, reason, first_seen_at) VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT DO NOTHING", canonicalId, utc(ts), mm, reason, utc(seenAt));
    }
}

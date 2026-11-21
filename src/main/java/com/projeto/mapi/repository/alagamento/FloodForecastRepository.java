package com.projeto.mapi.repository.alagamento;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Persistência do alerta por regressão (V38). Só cresce: uma linha por ponto e ciclo. */
@Repository
@RequiredArgsConstructor
public class FloodForecastRepository {

    public record Row(UUID cycleId, long floodPointId, Instant evaluatedAt, Instant issuedAt, String level,
                      Integer worstHorizonHours, String reason, Double hNowCm, String floodVersion, String rainModelVersion,
                      String bucketVersion, String codeVersion, String payloadJson, String rainStation, Double rainStationKm,
                      boolean shadow) {}

    /** Linha mais nova de um ponto, com o contexto que a API guardou. */
    public record Latest(long floodPointId, String slug, String name, Instant evaluatedAt, String payloadJson,
                         String rainStation, String rainStationName, Double rainStationKm, String level, String reason,
                         boolean shadow) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public void insert(Row r) {
        jdbc.update("""
                INSERT INTO flood_forecast_point (cycle_id, flood_point_id, evaluated_at, issued_at, level, worst_horizon_hours,
                    reason, h_now_cm, flood_version, rain_model_version, bucket_version, code_version, payload, rain_station,
                    rain_station_km, shadow)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)""",
                r.cycleId(), r.floodPointId(), utc(r.evaluatedAt()), utc(r.issuedAt()), r.level(), r.worstHorizonHours(),
                r.reason(), r.hNowCm(), r.floodVersion(), r.rainModelVersion(), r.bucketVersion(), r.codeVersion(),
                r.payloadJson(), r.rainStation(), r.rainStationKm(), r.shadow());
    }

    /** Nome da estação canônica (V25); nulo se não houver. */
    public String stationName(String canonicalId) {
        if (canonicalId == null) return null;
        List<String> n = jdbc.queryForList("SELECT name FROM station_canonical WHERE canonical_id = ?", String.class, canonicalId);
        return n.isEmpty() ? null : n.get(0);
    }

    /** A linha mais nova de cada ponto ativo (índice flood_forecast_point_latest). */
    public List<Latest> latestPerActivePoint() {
        return jdbc.query("""
                SELECT p.id, p.slug, p.name, f.evaluated_at, f.payload::text AS payload, f.rain_station, sc.name AS station_name,
                       f.rain_station_km,
                       f.level, f.reason, f.shadow
                FROM flood_points p
                JOIN LATERAL (SELECT * FROM flood_forecast_point x WHERE x.flood_point_id = p.id
                              ORDER BY x.evaluated_at DESC LIMIT 1) f ON TRUE
                LEFT JOIN station_canonical sc ON sc.canonical_id = f.rain_station
                WHERE p.active
                ORDER BY p.id""",
                (rs, i) -> {
                    Timestamp t = rs.getTimestamp("evaluated_at");
                    return new Latest(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                            t == null ? null : t.toInstant(), rs.getString("payload"), rs.getString("rain_station"), rs.getString("station_name"),
                            rs.getObject("rain_station_km", Double.class), rs.getString("level"), rs.getString("reason"),
                            rs.getBoolean("shadow"));
                });
    }
}

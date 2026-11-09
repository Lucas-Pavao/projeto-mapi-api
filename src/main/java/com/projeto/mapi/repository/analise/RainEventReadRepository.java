package com.projeto.mapi.repository.analise;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Leituras da série consolidada para a tabela de eventos de chuva (ADR 0017). Só leitura; tudo filtrado por fonte,
 * estação e intervalo de tempo (índice da hypertable em {@code ts_utc}).
 */
@Repository
@RequiredArgsConstructor
public class RainEventReadRepository {

    /** Uma hora da grade (observation_hourly_v); {@code value} nulo = hora incompleta. */
    public record HourRow(Instant hourEndUtc, Double value, int nReadings, int nExpected, List<UUID> runIds,
                          int maxRevision) {}

    /** Estação mais próxima de uma coordenada, com a distância haversine em metros. */
    public record StationRow(String stationId, double distanceM, String alertLevelM) {}

    /** Fuso declarado e estado de verificação da fonte (source_registry). */
    public record SourceTz(String sourceTz, String verificationState) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Horas de {@code variable} com fim em [from, to] de uma estação; só as horas que a view produziu. */
    public List<HourRow> hourly(String source, String stationId, String variable, Instant from, Instant to) {
        return jdbc.query("""
                SELECT hour_end_utc, value, n_readings, n_expected, run_ids, max_revision
                FROM observation_hourly_v
                WHERE source = ? AND station_id = ? AND variable = ? AND hour_end_utc >= ? AND hour_end_utc <= ?
                ORDER BY hour_end_utc
                """, (rs, i) -> new HourRow(rs.getObject("hour_end_utc", OffsetDateTime.class).toInstant(),
                rs.getObject("value", Double.class), rs.getInt("n_readings"), rs.getInt("n_expected"),
                uuids(rs.getArray("run_ids")), rs.getInt("max_revision")), source, stationId, variable, utc(from), utc(to));
    }

    /**
     * Leituras sub-horárias de chuva (mm por leitura) com carimbo em (from, to], descartando a leitura cuja anterior
     * está a mais de um passo (janela desconhecida; mesma regra da grade horária, V15).
     */
    public Map<Instant, Double> subHourlyRain(String source, String stationId, String resolution, Instant from,
                                              Instant to) {
        Map<Instant, Double> out = new HashMap<>();
        jdbc.query("""
                SELECT ts_utc, value FROM (
                    SELECT ts_utc, value,
                           ts_utc - lag(ts_utc) OVER (ORDER BY ts_utc) AS gap_before
                    FROM observation_current
                    WHERE source = ? AND station_id = ? AND variable = 'precipitation' AND resolution = ?
                      AND value IS NOT NULL AND ts_utc > ? AND ts_utc <= ?
                ) s
                WHERE gap_before IS NOT NULL AND gap_before <= CAST(? AS interval)
                """, rs -> {
            out.put(rs.getObject("ts_utc", OffsetDateTime.class).toInstant(), rs.getDouble("value"));
        }, source, stationId, resolution, utc(from), utc(to), stepOf(resolution));
        return out;
    }

    /** Leituras vigentes de {@code variable} (qualquer resolução) com carimbo em (from, to]. */
    public java.util.NavigableMap<Instant, Double> readings(String source, String stationId, String variable,
                                                           Instant from, Instant to) {
        java.util.NavigableMap<Instant, Double> out = new java.util.TreeMap<>();
        jdbc.query("""
                SELECT ts_utc, value FROM observation_current
                WHERE source = ? AND station_id = ? AND variable = ? AND value IS NOT NULL AND ts_utc > ? AND ts_utc <= ?
                """, rs -> {
            out.put(rs.getObject("ts_utc", OffsetDateTime.class).toInstant(), rs.getDouble("value"));
        }, source, stationId, variable, utc(from), utc(to));
        return out;
    }

    private static String stepOf(String resolution) {
        return switch (resolution) {
            case "PT5M" -> "5 minutes";
            case "PT10M" -> "10 minutes";
            case "PT15M" -> "15 minutes";
            default -> "1 hour";
        };
    }

    /**
     * Estação da fonte mais próxima entre as que têm {@code variable} na {@code resolution} dada (nula = qualquer);
     * empate pelo código.
     */
    public Optional<StationRow> nearestStation(String source, String variable, String resolution, double lat,
                                               double lon) {
        return jdbc.query("""
                SELECT s.station_id, s.attrs ->> 'alertLevelM' AS alert,
                       2 * 6371000 * asin(sqrt(power(sin(radians(s.lat - ?) / 2), 2)
                         + cos(radians(?)) * cos(radians(s.lat)) * power(sin(radians(s.lon - ?) / 2), 2))) AS d
                FROM station s
                WHERE s.source = ? AND s.lat IS NOT NULL AND s.lon IS NOT NULL
                  AND EXISTS (SELECT 1 FROM observation_current o WHERE o.source = s.source
                              AND o.station_id = s.station_id AND o.variable = ?
                              AND (CAST(? AS varchar) IS NULL OR o.resolution = CAST(? AS varchar)))
                ORDER BY d, s.station_id LIMIT 1
                """, (rs, i) -> new StationRow(rs.getString("station_id"), rs.getDouble("d"), rs.getString("alert")),
                lat, lat, lon, source, variable, resolution, resolution).stream().findFirst();
    }

    /** Distância (m) da coordenada à estação; vazio se a estação não existe ou não tem coordenada. */
    public Optional<Double> distanceTo(String source, String stationId, double lat, double lon) {
        return jdbc.query("""
                SELECT 2 * 6371000 * asin(sqrt(power(sin(radians(s.lat - ?) / 2), 2)
                         + cos(radians(?)) * cos(radians(s.lat)) * power(sin(radians(s.lon - ?) / 2), 2))) AS d
                FROM station s WHERE s.source = ? AND s.station_id = ? AND s.lat IS NOT NULL AND s.lon IS NOT NULL
                """, (rs, i) -> rs.getDouble("d"), lat, lat, lon, source, stationId).stream().findFirst();
    }

    /** linkage_config SENSOR_POINT com o raio dado (o de menor id), para o vínculo da chuva medida (ADR 0018). */
    public record SensorLinkage(long id, double radiusM, String method) {}

    public Optional<SensorLinkage> sensorLinkage(double radiusM) {
        return jdbc.query("SELECT id, radius_m, method FROM linkage_config WHERE kind = 'SENSOR_POINT' AND radius_m = ? "
                + "ORDER BY id LIMIT 1", (rs, i) -> new SensorLinkage(rs.getLong(1), rs.getDouble(2), rs.getString(3)),
                radiusM).stream().findFirst();
    }

    public Optional<SourceTz> sourceTz(String source) {
        return jdbc.query("SELECT source_tz, verification_state FROM source_registry WHERE source = ?",
                (rs, i) -> new SourceTz(rs.getString(1), rs.getString(2)), source).stream().findFirst();
    }

    private static List<UUID> uuids(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) return List.of();
        Object[] raw = (Object[]) array.getArray();
        return java.util.Arrays.stream(raw).map(o -> UUID.fromString(o.toString())).sorted().toList();
    }
}

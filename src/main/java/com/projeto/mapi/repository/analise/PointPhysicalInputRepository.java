package com.projeto.mapi.repository.analise;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** Leituras das entradas físicas do modelo v3 na previsão ao vivo; mesmas regras de {@code point_physical_inputs_v} (V27). */
@Repository
@RequiredArgsConstructor
public class PointPhysicalInputRepository {

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /**
     * Maior bloco de 10 min da hora na estação canônica e fonte dadas. Vazio se não há bloco ou se algum bloco maior
     * que 10 min tem chuva (o pico dentro dele não é conhecido); bloco longo com 0 mm vale 0.
     */
    public Optional<Double> rainMax10min(String canonicalId, String source, Instant hourEnd) {
        return jdbc.query("""
                SELECT CASE WHEN bool_and(o.ts_utc - o.interval_start_utc <= interval '10 minutes' OR o.value = 0)
                            THEN max(o.value) END
                FROM observation_current o
                JOIN station_alias a ON a.source = o.source AND a.station_id = o.station_id
                WHERE a.canonical_id = ? AND o.source = ? AND o.variable = 'precipitation' AND o.value IS NOT NULL
                  AND o.ts_utc > ? AND o.ts_utc <= ?
                """, (rs, i) -> rs.getObject(1, Double.class), canonicalId, source, utc(hourEnd.minusSeconds(3600)),
                utc(hourEnd)).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** Régua do Duas Unas (APAC), média da hora em metros, só para coordenada a até 1 km da régua. */
    public Optional<Double> riverLevelM(double lat, double lon, Instant hourEnd) {
        return jdbc.query("""
                SELECT avg(d.water_level) / 100.0 FROM sensor_data d
                WHERE d.sensor_id = 'APAC-RIO-260790119H' AND d.water_level IS NOT NULL
                  AND ST_DistanceSphere(ST_MakePoint(?, ?), ST_MakePoint(d.longitude, d.latitude)) <= 1000
                  AND d.timestamp > (? AT TIME ZONE 'America/Recife') AND d.timestamp <= (? AT TIME ZONE 'America/Recife')
                """, (rs, i) -> rs.getObject(1, Double.class), lon, lat, utc(hourEnd.minusSeconds(3600)), utc(hourEnd))
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** Umidade do solo 0-7 cm do ERA5-Land (reanálise) no instante, na grade do ponto (estação a até 200 m). */
    public Optional<Double> soilMoisture(double lat, double lon, Instant ts) {
        return jdbc.query("""
                SELECT s.value FROM observation_current s
                JOIN station es ON es.source = s.source AND es.station_id = s.station_id
                WHERE s.source = 'OPEN_METEO_ERA5LAND' AND s.variable = 'soil_moisture_0_7cm' AND s.resolution = 'PT1H'
                  AND s.ts_utc = ? AND ST_DistanceSphere(ST_MakePoint(?, ?), ST_MakePoint(es.lon, es.lat)) <= 200
                LIMIT 1
                """, (rs, i) -> rs.getObject(1, Double.class), utc(ts), lon, lat).stream()
                .filter(java.util.Objects::nonNull).findFirst();
    }
}

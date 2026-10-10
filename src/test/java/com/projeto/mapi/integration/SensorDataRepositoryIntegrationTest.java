package com.projeto.mapi.integration;

import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Queries que o H2 não executa: time_bucket (TimescaleDB) e DISTINCT ON (Postgres), além do filtro
 * de raio (acos/radians) contra a hypertable real.
 */
class SensorDataRepositoryIntegrationTest extends AbstractTimescaleIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2024, 5, 10, 10, 0);

    @Autowired
    private SensorDataRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpaESemeia() {
        repository.deleteAll();
        // A e B a ~1 km do centro (-8.05, -34.88); LONGE a ~140 km (fora de qualquer raio de 3 km).
        repository.saveAll(List.of(
                sensor("A", -8.0500, -34.8800, T0.plusMinutes(5), 2.0),
                sensor("A", -8.0500, -34.8800, T0.plusMinutes(35), 4.0),
                sensor("A", -8.0500, -34.8800, T0.plusMinutes(70), 10.0),
                sensor("B", -8.0590, -34.8800, T0.plusMinutes(10), 6.0),
                sensor("LONGE", -9.0, -35.8, T0.plusMinutes(10), 99.0)));
    }

    @Test
    void timeBucketAgregaPorHora() {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT time_bucket('1 hour', timestamp) AS bucket, sensor_id, avg(accumulated_precipitation) AS media "
                        + "FROM sensor_data WHERE sensor_id = 'A' GROUP BY bucket, sensor_id ORDER BY bucket");
        assertThat(linhas).hasSize(2);
        assertThat(((Number) linhas.get(0).get("media")).doubleValue()).isEqualTo(3.0); // (2+4)/2 na hora das 10h
        assertThat(((Number) linhas.get(1).get("media")).doubleValue()).isEqualTo(10.0); // hora das 11h
    }

    @Test
    void findSensorsByRadiusExcluiSensoresDistantes() {
        List<SensorData> noRaio = repository.findSensorsByRadius(
                -8.05, -34.88, 3.0, T0.minusHours(1), T0.plusHours(3));
        assertThat(noRaio).extracting(SensorData::getSensorId).containsOnly("A", "B");
        assertThat(noRaio).hasSize(4);
    }

    @Test
    void findAllLatestUsaDistinctOnDoPostgres() {
        List<SensorData> ultimos = repository.findAllLatest(T0.minusDays(1));
        assertThat(ultimos).hasSize(3); // um por sensor
        assertThat(ultimos).filteredOn(s -> s.getSensorId().equals("A"))
                .singleElement().satisfies(s -> assertThat(s.getTimestamp()).isEqualTo(T0.plusMinutes(70)));
    }

    private static SensorData sensor(String id, double lat, double lon, LocalDateTime ts, double chuva) {
        return SensorData.builder().sensorId(id).latitude(lat).longitude(lon)
                .timestamp(ts).accumulatedPrecipitation(chuva).build();
    }
}

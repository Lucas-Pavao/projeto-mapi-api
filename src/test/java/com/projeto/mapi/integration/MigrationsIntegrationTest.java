package com.projeto.mapi.integration;

import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que as migrations Flyway reais rodam num TimescaleDB de verdade e que o schema resultante
 * é o esperado. O fato de o contexto subir já prova o ddl-auto=validate (entidades x schema).
 */
class MigrationsIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsAplicadasComSucesso() {
        List<String> versoes = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank", String.class);
        assertThat(versoes).contains("1", "2");
    }

    @Test
    void asQuatroHypertablesExistem() {
        List<String> hypertables = jdbc.queryForList(
                "SELECT hypertable_name FROM timescaledb_information.hypertables", String.class);
        assertThat(hypertables).containsExactlyInAnyOrder(
                "sensor_data", "weather_data", "flood_predictions", "flood_scenario_labels");
    }

    @Test
    void colunasDaV2ExistemEmSensorData() {
        List<String> colunas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'sensor_data'", String.class);
        assertThat(colunas).contains("river_name", "river_pre_alert_level", "river_alert_level", "river_flood_level");
    }
}

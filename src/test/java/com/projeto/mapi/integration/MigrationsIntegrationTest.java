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

    @Test
    void v7CriaPostgisEAmostragemDeRasterEm31985Funciona() {
        // A Fase 3 amostra o MDT do PE3D (EPSG:31985) no banco; isto prova que a extensão raster responde.
        assertThat(jdbc.queryForList("SELECT extname FROM pg_extension", String.class))
                .contains("postgis", "postgis_raster");
        Double valor = jdbc.queryForObject("""
                SELECT ST_Value(
                         ST_SetValue(ST_AddBand(ST_MakeEmptyRaster(10, 10, 290000, 9110000, 1, -1, 0, 0, 31985),
                                                '32BF'::text, -9999, -9999),
                                     1, 5, 5, 11.11),
                         1, ST_SetSRID(ST_MakePoint(290004.5, 9109995.5), 31985))
                """, Double.class);
        assertThat(valor).isCloseTo(11.11, org.assertj.core.data.Offset.offset(1e-4));
    }
}

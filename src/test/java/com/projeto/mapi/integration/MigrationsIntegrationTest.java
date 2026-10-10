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
        assertThat(versoes).contains("1", "2", "3", "4", "5", "6");
    }

    @Test
    void asQuatroHypertablesExistem() {
        List<String> hypertables = jdbc.queryForList(
                "SELECT hypertable_name FROM timescaledb_information.hypertables", String.class);
        assertThat(hypertables).containsExactlyInAnyOrder(
                "sensor_data", "weather_data", "flood_predictions", "flood_scenario_labels",
                "raw_observation", "observation");
    }

    @Test
    void colunasDaV2ExistemEmSensorData() {
        List<String> colunas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'sensor_data'", String.class);
        assertThat(colunas).contains("river_name", "river_pre_alert_level", "river_alert_level", "river_flood_level");
    }

    @Test
    void tabelasDoPipelineExistemEMigracoesV3aV7Aplicadas() {
        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tabelas).contains("ingestion_run", "station", "variable_catalog", "source_registry",
                "raw_observation", "ingestion_quarantine", "observation", "observation_revision",
                "observation_current", "observation_hourly_v", "flood_occurrence", "occurrence_point_match",
                "label_filter", "linkage_config", "point_station_link");
        List<String> versoes = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true", String.class);
        assertThat(versoes).contains("3", "4", "5", "6", "7");
    }

    /** T8: a migração do pipeline é aditiva; nenhuma instrução destrutiva em V3 a V6. */
    @Test
    void migracoesV3aV6NaoTemInstrucaoDestrutiva() throws Exception {
        java.util.regex.Pattern proibido = java.util.regex.Pattern.compile(
                "\\b(DROP\\s+(TABLE|COLUMN|VIEW|INDEX)|DELETE\\s+FROM|TRUNCATE|ALTER\\s+TABLE\\s+\\S+\\s+(ALTER\\s+COLUMN\\s+\\S+\\s+(SET\\s+DATA\\s+)?TYPE|DROP))",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        for (String nome : List.of("V3__ingestion_registry", "V4__raw_observation_and_quarantine",
                "V5__observation_timeseries", "V6__labels_and_linkage")) {
            try (var in = getClass().getResourceAsStream("/db/migration/" + nome + ".sql")) {
                assertThat(in).as(nome).isNotNull();
                String sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replaceAll("(?m)--.*$", "");
                assertThat(proibido.matcher(sql).find()).as("instrução destrutiva em " + nome).isFalse();
            }
        }
    }

    /** O baseline antigo fica intocado: tabelas congeladas continuam existindo. */
    @Test
    void tabelasAntigasContinuamExistindo() {
        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tabelas).contains("sensor_data", "weather_data", "hourly_precipitation");
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

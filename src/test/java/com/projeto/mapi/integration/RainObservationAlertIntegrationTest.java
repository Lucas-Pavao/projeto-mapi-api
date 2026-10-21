package com.projeto.mapi.integration;

import com.projeto.mapi.service.alert.AlertEvaluator;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão 45: RAIN_RATE lê a soma horária de observation.precipitation (série consolidada, só ACTIVE), não o
 * sensor_data. Três horas com somas conhecidas; o alerta só dispara em >= 55 mm.
 */
class RainObservationAlertIntegrationTest extends AbstractApiIntegrationTest {

    static final String SRC = "TST_RAIN_ALERT";

    @Autowired AlertEvaluator evaluator;
    @Autowired JdbcTemplate jdbc;

    UUID run;
    Instant h0;

    @BeforeEach
    void prepara() {
        jdbc.update("DELETE FROM alert_state");
        jdbc.update("DELETE FROM alert");
        jdbc.update("DELETE FROM alert_rule");
        ObservationFixtures.clear(jdbc, SRC);
        run = ObservationFixtures.run(jdbc, SRC);
        h0 = Instant.now().truncatedTo(ChronoUnit.HOURS); // fim da última hora fechada
        jdbc.update("INSERT INTO alert_rule (kind, threshold, threshold_source, enabled, created_by, created_at) "
                + "VALUES ('RAIN_RATE', 55, 'IDF TR 2 anos', TRUE, 'teste', now())");
    }

    private long alertas() {
        return jdbc.queryForObject("SELECT count(*) FROM alert WHERE kind = 'RAIN_RATE'", Long.class);
    }

    @Test
    void tresHorasComSomasConhecidas_soAlertaEm55OuMais() {
        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-1", h0.minus(2, ChronoUnit.HOURS), 10.0);
        assertThat(evaluator.evaluate()).isZero();

        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-1", h0.minus(1, ChronoUnit.HOURS), 54.9);
        assertThat(evaluator.evaluate()).as("54,9 mm < 55").isZero();

        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-1", h0, 55.0);
        assertThat(evaluator.evaluate()).as("55,0 mm >= 55").isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT station_id, observed_value, severity FROM alert WHERE kind = 'RAIN_RATE'"))
                .containsEntry("station_id", "EST-1").containsEntry("observed_value", 55.0).containsEntry("severity", "WARNING");

        assertThat(evaluator.evaluate()).as("mesma hora de novo: sem repetir").isZero();
        assertThat(alertas()).isEqualTo(1);
    }

    @Test
    void somaDeLeiturasDeQuinzeMinutosFormaAHora_eHoraIncompletaNaoVira() {
        // EST-2: 4 leituras de 15 min somando 56 mm na hora h0 -> alerta
        for (int i = 0; i < 4; i++) {
            ObservationFixtures.precipitation(jdbc, run, SRC, "EST-2", "PT15M", h0.minus(45L - 15L * i, ChronoUnit.MINUTES), 14.0, "ACTIVE");
        }
        // EST-3: uma única leitura de 90 mm numa hora de 4 esperadas (completude < 80%): valor nulo, não entra
        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-3", "PT15M", h0, 90.0, "ACTIVE");

        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT station_id FROM alert WHERE kind = 'RAIN_RATE'", String.class)).isEqualTo("EST-2");
        assertThat(jdbc.queryForObject("SELECT observed_value FROM alert WHERE kind = 'RAIN_RATE'", Double.class)).isEqualTo(56.0);
    }

    @Test
    void registroRetratadoNaoContaEHoraAntigaNaoVale() {
        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-4", "PT1H", h0, 120.0, "RETRACTED");
        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-5", h0.minus(6, ChronoUnit.HOURS), 120.0); // fora da janela de 3 h
        assertThat(evaluator.evaluate()).isZero();
    }

    @Test
    void regraDeUmaEstacaoCasaPeloCodigoComOuSemPrefixoDoSensor() {
        jdbc.update("UPDATE alert_rule SET station_id = 'APAC-PLUVIO-EST-6'");
        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-6", h0, 60.0);
        ObservationFixtures.precipitation(jdbc, run, SRC, "EST-7", h0, 70.0);
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT station_id FROM alert WHERE kind = 'RAIN_RATE'", String.class)).isEqualTo("EST-6");
    }
}

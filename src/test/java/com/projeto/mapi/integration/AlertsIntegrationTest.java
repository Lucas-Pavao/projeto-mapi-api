package com.projeto.mapi.integration;

import com.projeto.mapi.dto.AlagamentoPontoDTO;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.service.alert.AlertEvaluator;
import com.projeto.mapi.service.alagamento.FloodForecastService;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 4: regras, alertas, ack e AlertEvaluator (transição, supressão de 30 min, padrões do ADR 0011). */
class AlertsIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired AlertEvaluator evaluator;
    @Autowired FloodPointRepository pointRepository;
    @Autowired FloodEventRepository eventRepository;
    @Autowired FloodPredictionRepository predictionRepository;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean FloodForecastService floodForecast;
    @MockitoBean SensorService sensorService;
    @MockitoBean com.projeto.mapi.service.sensor.HourlyRainService hourlyRain;
    @MockitoBean TideService tideService;

    @BeforeEach
    void limpa() {
        jdbc.update("DELETE FROM alert_state");
        jdbc.update("DELETE FROM alert");
        jdbc.update("DELETE FROM alert_rule");
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();
        pointRepository.save(FloodPoint.builder().slug("p-alerta").name("Ponto alerta").latitude(-8.05).longitude(-34.88).build());
        when(sensorService.getLatestWithFreshness(any())).thenReturn(List.of());
        when(hourlyRain.latestClosedHour()).thenReturn(List.of());
        when(floodForecast.shadow()).thenReturn(false);
        when(floodForecast.current()).thenReturn(List.of());
    }

    private void regra(String kind, String minRisk, Double threshold, String source) {
        jdbc.update("INSERT INTO alert_rule (kind, min_risk, threshold, threshold_source, enabled, created_by, created_at) "
                + "VALUES (?,?,?,?,TRUE,'teste',now())", kind, minRisk, threshold, source);
    }

    /** Nível do alerta por regressão do ponto no ciclo (ALERTA, ATENCAO, SEM_ALERTA, SEM_DADO com motivo). */
    private void nivel(String level, String reason) {
        boolean semDado = "SEM_DADO".equals(level);
        when(floodForecast.current()).thenReturn(List.of(new AlagamentoPontoDTO(1L, "p-alerta", "Ponto alerta", level,
                semDado ? null : 1, reason, semDado ? "previsão de chuva (rain_v1) fora do ar" : null,
                "ALERTA".equals(level) ? 17.7 : 0.0, 0.0, List.of(), null, null, null, null, null, Map.of(), List.of(), List.of(),
                null, Instant.now(), Instant.now(), true, false, "regra", AlagamentoPontoDTO.ATTRIBUTION)));
    }

    private void previsao(String level) {
        nivel(level, null);
    }

    private static com.projeto.mapi.service.sensor.HourlyRainService.StationRain rain(String id, double mm) {
        return new com.projeto.mapi.service.sensor.HourlyRainService.StationRain("T", id, Instant.now(), mm, null, null);
    }

    private long alertas() {
        return jdbc.queryForObject("SELECT count(*) FROM alert", Long.class);
    }

    @Test
    void soAlerta_entraNaTransicao_saiComAviso_eSupressaoDe30Min() {
        regra("RISK_LEVEL", "HIGH", null, null); // "Só alerta"

        previsao("SEM_ALERTA");
        assertThat(evaluator.evaluate()).isZero();

        previsao("ALERTA");
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(evaluator.evaluate()).as("segundo ciclo igual não repete").isZero();
        assertThat(jdbc.queryForMap("SELECT severity, model_validation_status, risk_level, title, detail FROM alert"))
                .containsEntry("severity", "CRITICAL").containsEntry("model_validation_status", "NOT_VALIDATED")
                .containsEntry("risk_level", "ALERTA").containsEntry("title", "Ponto alerta: alerta em 1 h · lâmina 17,7 cm")
                .containsEntry("detail", "Alerta experimental (chuva prevista + balde), não validado.");

        // sai: aviso "saiu do alerta"; volta dentro de 30 min: suprimido
        previsao("SEM_ALERTA");
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM alert ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("Ponto alerta: saiu do alerta");
        previsao("ALERTA");
        assertThat(evaluator.evaluate()).isZero();
        assertThat(alertas()).isEqualTo(2);

        // passados 30 min do primeiro alerta, sai e volta: alerta novo
        previsao("SEM_ALERTA");
        evaluator.evaluate();
        jdbc.update("UPDATE alert SET triggered_at = now() - interval '31 minutes'");
        previsao("ALERTA");
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(alertas()).isEqualTo(4);
    }

    @Test
    void atencaoOuAlerta_escalaDeAtencaoParaAlerta_eSupressaoVaiPorNivel() {
        regra("RISK_LEVEL", "MEDIUM", null, null); // "Atenção ou alerta"

        previsao("SEM_ALERTA");
        assertThat(evaluator.evaluate()).isZero();

        previsao("ATENCAO");
        assertThat(evaluator.evaluate()).as("entrada em ATENÇÃO").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT severity FROM alert ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("WARNING");

        previsao("ALERTA");
        assertThat(evaluator.evaluate()).as("escalada ATENÇÃO -> ALERTA, dentro dos 30 min").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT severity FROM alert ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("CRITICAL");
        assertThat(evaluator.evaluate()).as("ALERTA repetido: nada").isZero();

        // desce para ATENÇÃO (continua em condição): sem alerta; volta a ALERTA dentro de 30 min: suprimido
        previsao("ATENCAO");
        assertThat(evaluator.evaluate()).isZero();
        previsao("ALERTA");
        assertThat(evaluator.evaluate()).as("mesmo nível dentro da supressão").isZero();
        assertThat(alertas()).isEqualTo(2);
    }

    @Test
    void semDadoNaoEhSaidaDoAlerta_eGeraAvisoComMotivo() {
        regra("RISK_LEVEL", "HIGH", null, null);
        regra("PREDICTION_UNAVAILABLE", null, null, null);

        previsao("ALERTA");
        assertThat(evaluator.evaluate()).isEqualTo(1);

        nivel("SEM_DADO", "PREVISAO_INDISPONIVEL");
        assertThat(evaluator.evaluate()).as("só o aviso de sem dado; nada de 'saiu do alerta'").isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT kind, degradation_reason, title FROM alert WHERE kind='PREDICTION_UNAVAILABLE'"))
                .containsEntry("degradation_reason", "PREVISAO_INDISPONIVEL").containsEntry("title", "Ponto alerta: alerta sem dado");

        // ALERTA de novo logo depois do SEM_DADO: o estado de RISK_LEVEL não foi zerado, então não é "entrada" nova
        previsao("ALERTA");
        assertThat(evaluator.evaluate()).isZero();
    }

    @Test
    void modoSombra_naoAvaliaAsRegrasDoAlertaPorRegressao() {
        regra("RISK_LEVEL", "MEDIUM", null, null);
        when(floodForecast.shadow()).thenReturn(true);
        previsao("ALERTA");
        assertThat(evaluator.evaluate()).isZero();
        assertThat(alertas()).isZero();
    }

    @Test
    void chuva55mmEm1h_naoDisparaAbaixoDoLimiar() {
        regra("RAIN_RATE", null, 55.0, "IDF TR 2 anos");
        when(hourlyRain.latestClosedHour()).thenReturn(List.of(rain("S-OK", 54.9)));
        assertThat(evaluator.evaluate()).isZero();
        when(hourlyRain.latestClosedHour()).thenReturn(List.of(rain("S-OK", 54.9), rain("S-FORTE", 55.0)));
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT station_id, kind, observed_value FROM alert"))
                .containsEntry("station_id", "S-FORTE").containsEntry("kind", "RAIN_RATE").containsEntry("observed_value", 55.0);
    }

    @Test
    void pluviometroSemResposta30Min() {
        regra("SENSOR_STALE", null, 30.0, "ADR 0011");
        when(sensorService.getLatestWithFreshness(any())).thenReturn(List.of(
                SensorResponseDTO.builder().sensorId("P-VIVO").type("PRECIPITATION").ageSeconds(120L).build(),
                SensorResponseDTO.builder().sensorId("P-MUDO").type("PRECIPITATION").ageSeconds(31 * 60L).build(),
                SensorResponseDTO.builder().sensorId("R-MUDO").type("RIVER_LEVEL").ageSeconds(99 * 60L).build()));
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT station_id FROM alert", String.class)).isEqualTo("P-MUDO");
    }

    @Test
    void rioNaCotaDeAlerta_eMareComFonte() {
        regra("RIVER_STAGE", null, null, null);
        when(sensorService.getLatestWithFreshness(any())).thenReturn(List.of(
                SensorResponseDTO.builder().sensorId("RIO-1").stationName("Rio Um").waterLevel(3.1).riverAlertLevel(3.0).riverFloodLevel(4.0).build(),
                SensorResponseDTO.builder().sensorId("RIO-2").waterLevel(1.0).riverAlertLevel(3.0).build()));
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT severity FROM alert", String.class)).isEqualTo("WARNING");

        regra("TIDE_ABOVE", null, 2.0, "tábua do porto");
        when(tideService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(2.3);
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT kind FROM alert WHERE kind='TIDE_ABOVE'", String.class)).isEqualTo("TIDE_ABOVE");
    }

    @Test
    void regraDesativadaNaoDispara() {
        regra("RISK_LEVEL", "HIGH", null, null);
        jdbc.update("UPDATE alert_rule SET enabled = FALSE");
        previsao("ALERTA");
        assertThat(evaluator.evaluate()).isZero();
    }

    @Test
    void v18SemeiaAsCincoRegrasPadraoSemRegraDeMare() throws Exception {
        // As regras foram apagadas no @BeforeEach; confere o script que as insere.
        String sql = new String(getClass().getResourceAsStream("/db/migration/V18__alert_rule_alert.sql").readAllBytes());
        assertThat(sql).contains("('RISK_LEVEL', 'HIGH'", "('RIVER_STAGE'", "('SENSOR_STALE', NULL, 30", "('RAIN_RATE', NULL, 55", "('PREDICTION_UNAVAILABLE'");
        assertThat(sql).doesNotContain("('TIDE_ABOVE',");
    }

    // ---------- API ----------

    @Test
    void alertasERegras_matrizDePerfis() throws Exception {
        regra("RISK_LEVEL", "HIGH", null, null);
        previsao("ALERTA");
        evaluator.evaluate();
        Long alertId = jdbc.queryForObject("SELECT id FROM alert", Long.class);
        Long ruleId = jdbc.queryForObject("SELECT id FROM alert_rule", Long.class);
        String ruleBody = "{\"kind\":\"RISK_LEVEL\",\"minRisk\":\"MEDIUM\",\"enabled\":true}";

        for (String t : new String[]{null, userToken()}) {
            mockMvc.perform(as(get("/api/alerts"), t)).andExpect(status().isForbidden());
            mockMvc.perform(as(post("/api/alerts/" + alertId + "/ack"), t)).andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/alert-rules"), t)).andExpect(status().isForbidden());
            mockMvc.perform(as(post("/api/alert-rules"), t).contentType(MediaType.APPLICATION_JSON).content(ruleBody)).andExpect(status().isForbidden());
            mockMvc.perform(as(put("/api/alert-rules/" + ruleId), t).contentType(MediaType.APPLICATION_JSON).content(ruleBody)).andExpect(status().isForbidden());
            mockMvc.perform(as(delete("/api/alert-rules/" + ruleId), t)).andExpect(status().isForbidden());
        }
        for (String t : new String[]{operadorToken(), adminToken()}) {
            mockMvc.perform(as(get("/api/alerts"), t)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].floodPointSlug").value("p-alerta"))
                    .andExpect(jsonPath("$.content[0].modelValidationStatus").value("NOT_VALIDATED"));
            mockMvc.perform(as(get("/api/alert-rules"), t)).andExpect(status().isOk()).andExpect(jsonPath("$[0].channels[0]").value("BROWSER"));
        }
    }

    @Test
    void ackEhIdempotente_eListagemPorStatus() throws Exception {
        regra("RISK_LEVEL", "HIGH", null, null);
        previsao("ALERTA");
        evaluator.evaluate();
        Long id = jdbc.queryForObject("SELECT id FROM alert", Long.class);

        mockMvc.perform(as(post("/api/alerts/" + id + "/ack"), operadorToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledgedBy").value("it_operador"))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty());
        mockMvc.perform(as(post("/api/alerts/" + id + "/ack"), adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledgedBy").value("it_operador"));
        mockMvc.perform(as(post("/api/alerts/999999/ack"), operadorToken())).andExpect(status().isNotFound());

        mockMvc.perform(as(get("/api/alerts"), operadorToken())).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(as(get("/api/alerts?status=ACKNOWLEDGED"), operadorToken())).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(as(get("/api/alerts?status=ALL&from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z"), operadorToken()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(as(get("/api/alerts?status=XX"), operadorToken())).andExpect(status().isBadRequest());
    }

    @Test
    void crudDeRegras_comValidacao() throws Exception {
        String t = operadorToken();
        String r = mockMvc.perform(as(post("/api/alert-rules"), t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"RAIN_RATE\",\"threshold\":40,\"thresholdSource\":\"teste\",\"enabled\":true,\"floodPointSlug\":\"p-alerta\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value("it_operador"))
                .andExpect(jsonPath("$.floodPointSlug").value("p-alerta"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(r).get("id").asLong();

        // TIDE_ABOVE e RAIN_RATE exigem fonte do limiar
        mockMvc.perform(as(post("/api/alert-rules"), t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"TIDE_ABOVE\",\"threshold\":2.1,\"enabled\":true}")).andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/alert-rules"), t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"RISK_LEVEL\",\"enabled\":true}")).andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/alert-rules"), t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"NADA\",\"enabled\":true}")).andExpect(status().isBadRequest());

        mockMvc.perform(as(put("/api/alert-rules/" + id), t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"RAIN_RATE\",\"threshold\":60,\"thresholdSource\":\"outra\",\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.threshold").value(60.0))
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.updatedAt").isNotEmpty());
        mockMvc.perform(as(put("/api/alert-rules/999999"), t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"RISK_LEVEL\",\"minRisk\":\"HIGH\",\"enabled\":true}")).andExpect(status().isNotFound());

        mockMvc.perform(as(delete("/api/alert-rules/" + id), t)).andExpect(status().isNoContent());
        mockMvc.perform(as(delete("/api/alert-rules/" + id), t)).andExpect(status().isNotFound());
    }

    @Test
    void apagarRegraMantemAlertasJaDisparados() throws Exception {
        regra("RISK_LEVEL", "HIGH", null, null);
        previsao("ALERTA");
        evaluator.evaluate();
        Long ruleId = jdbc.queryForObject("SELECT id FROM alert_rule", Long.class);
        mockMvc.perform(as(delete("/api/alert-rules/" + ruleId), operadorToken())).andExpect(status().isNoContent());
        assertThat(alertas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rule_id FROM alert", Long.class)).isNull();
    }
}

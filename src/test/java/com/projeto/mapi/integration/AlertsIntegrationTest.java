package com.projeto.mapi.integration;

import com.projeto.mapi.dto.DegradationReason;
import com.projeto.mapi.dto.ModelValidationStatus;
import com.projeto.mapi.dto.PredictionSummaryDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.service.alert.AlertEvaluator;
import com.projeto.mapi.service.flood.PredictionQueryService;
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

    @MockitoBean PredictionQueryService predictionQuery;
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
        when(predictionQuery.latestPerActivePoint()).thenReturn(List.of());
    }

    private void regra(String kind, String minRisk, Double threshold, String source) {
        jdbc.update("INSERT INTO alert_rule (kind, min_risk, threshold, threshold_source, enabled, created_by, created_at) "
                + "VALUES (?,?,?,?,TRUE,'teste',now())", kind, minRisk, threshold, source);
    }

    private void previsao(RiskLevel level, DegradationReason reason) {
        when(predictionQuery.latestPerActivePoint()).thenReturn(List.of(new PredictionSummaryDTO("p-alerta", level,
                level == RiskLevel.UNKNOWN ? null : 0.9, Instant.now(), null, 3, "m1", reason, null,
                ModelValidationStatus.NOT_VALIDATED)));
    }

    private static com.projeto.mapi.service.sensor.HourlyRainService.StationRain rain(String id, double mm) {
        return new com.projeto.mapi.service.sensor.HourlyRainService.StationRain("T", id, Instant.now(), mm, null, null);
    }

    private long alertas() {
        return jdbc.queryForObject("SELECT count(*) FROM alert", Long.class);
    }

    @Test
    void risco_alertaSoNaTransicao_comSupressaoDe30Min() {
        regra("RISK_LEVEL", "HIGH", null, null);

        previsao(RiskLevel.LOW, null);
        assertThat(evaluator.evaluate()).isZero();

        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(evaluator.evaluate()).as("segundo ciclo igual não repete").isZero();
        assertThat(alertas()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT severity, model_validation_status, risk_level FROM alert"))
                .containsEntry("severity", "CRITICAL").containsEntry("model_validation_status", "NOT_VALIDATED")
                .containsEntry("risk_level", "HIGH");

        // sai e volta dentro de 30 min: suprimido
        previsao(RiskLevel.LOW, null);
        evaluator.evaluate();
        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).isZero();
        assertThat(alertas()).isEqualTo(1);

        // passados 30 min do primeiro alerta, sai e volta: alerta novo
        previsao(RiskLevel.LOW, null);
        evaluator.evaluate();
        jdbc.update("UPDATE alert SET triggered_at = now() - interval '31 minutes'");
        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(alertas()).isEqualTo(2);
    }

    @Test
    void escaladaMediumParaHigh_geraAlertaMaiorMesmoDentroDe30Min_eSupressaoVaiPorNivel() {
        regra("RISK_LEVEL", "MEDIUM", null, null);

        previsao(RiskLevel.LOW, null);
        assertThat(evaluator.evaluate()).isZero();

        previsao(RiskLevel.MEDIUM, null);
        assertThat(evaluator.evaluate()).as("entrada em MEDIUM").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT severity FROM alert ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("WARNING");

        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).as("escalada MEDIUM -> HIGH, dentro dos 30 min").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT severity FROM alert ORDER BY id DESC LIMIT 1", String.class)).isEqualTo("CRITICAL");
        assertThat(alertas()).isEqualTo(2);

        assertThat(evaluator.evaluate()).as("HIGH repetido: nada").isZero();

        // desce para MEDIUM (continua em condição): sem alerta; volta a HIGH dentro de 30 min: mesmo nível, suprimido
        previsao(RiskLevel.MEDIUM, null);
        assertThat(evaluator.evaluate()).isZero();
        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).as("mesmo nível dentro da supressão").isZero();
        assertThat(alertas()).isEqualTo(2);

        // passados 30 min, uma nova escalada alerta de novo
        previsao(RiskLevel.MEDIUM, null);
        evaluator.evaluate();
        jdbc.update("UPDATE alert SET triggered_at = now() - interval '31 minutes'");
        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(alertas()).isEqualTo(3);
    }

    @Test
    void unknownNaoEhQuedaDeRisco_eGeraPredictionUnavailableComMotivo() {
        regra("RISK_LEVEL", "HIGH", null, null);
        regra("PREDICTION_UNAVAILABLE", null, null, null);

        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).isEqualTo(1);

        previsao(RiskLevel.UNKNOWN, DegradationReason.AI_TIMEOUT);
        assertThat(evaluator.evaluate()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT kind, degradation_reason FROM alert WHERE kind='PREDICTION_UNAVAILABLE'"))
                .containsEntry("degradation_reason", "AI_TIMEOUT");

        // HIGH de novo logo depois do UNKNOWN: o estado de RISK_LEVEL não foi zerado, então não é "entrada" nova
        previsao(RiskLevel.HIGH, null);
        assertThat(evaluator.evaluate()).isZero();
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
        previsao(RiskLevel.HIGH, null);
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
        previsao(RiskLevel.HIGH, null);
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
        previsao(RiskLevel.HIGH, null);
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
        previsao(RiskLevel.HIGH, null);
        evaluator.evaluate();
        Long ruleId = jdbc.queryForObject("SELECT id FROM alert_rule", Long.class);
        mockMvc.perform(as(delete("/api/alert-rules/" + ruleId), operadorToken())).andExpect(status().isNoContent());
        assertThat(alertas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rule_id FROM alert", Long.class)).isNull();
    }
}

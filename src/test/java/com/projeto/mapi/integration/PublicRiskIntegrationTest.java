package com.projeto.mapi.integration;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 9: GET /api/public/risk e o filtro de taxa (ADR 0013). */
class PublicRiskIntegrationTest extends AbstractApiIntegrationTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean com.projeto.mapi.service.alagamento.FloodForecastService floodForecast;

    private void alerta(boolean shadow, String... slugLevel) {
        org.mockito.Mockito.when(floodForecast.shadow()).thenReturn(shadow);
        java.util.List<com.projeto.mapi.dto.AlagamentoPontoDTO> l = new java.util.ArrayList<>();
        for (int i = 0; i < slugLevel.length; i += 2) {
            l.add(new com.projeto.mapi.dto.AlagamentoPontoDTO(i, slugLevel[i], slugLevel[i], slugLevel[i + 1], 1, null, null, 17.7, null,
                    java.util.List.of(), null, null, null, null, null, java.util.Map.of(), java.util.List.of(), java.util.List.of(), null,
                    Instant.now(), Instant.now(), true, shadow, "regra", com.projeto.mapi.dto.AlagamentoPontoDTO.ATTRIBUTION));
        }
        org.mockito.Mockito.when(floodForecast.current()).thenReturn(l);
    }

    @Autowired FloodPointRepository pointRepository;
    @Autowired FloodEventRepository eventRepository;
    @Autowired FloodPredictionRepository predictionRepository;
    @Autowired SensorDataRepository sensorRepository;
    @Autowired JdbcTemplate jdbc;

    private static int ipSeq = 0;

    private String ip() {
        return "10.9.0." + (++ipSeq);
    }

    @BeforeEach
    void semeia() {
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        sensorRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();
        FloodPoint alto = pointRepository.save(FloodPoint.builder().slug("pub-alto").name("Ponto alto").latitude(-8.0500).longitude(-34.8800).build());
        pointRepository.save(FloodPoint.builder().slug("pub-sem-previsao").name("Sem previsão").latitude(-8.1000).longitude(-34.9500).build());
        Instant agora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO flood_predictions (\"timestamp\", issued_at, flood_point_id, latitude, longitude, flood_probability, "
                        + "risk_level, status, model_version, model_version_full, horizon_hours) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                LocalDateTime.ofInstant(agora, ZoneId.systemDefault()), java.sql.Timestamp.from(agora), alto.getId(),
                -8.05, -34.88, 0.91, "HIGH", "SUCCESS", "m1", "m1+abc", 3);
        // chuva da última hora: soma horária de observation.precipitation (decisão 45)
        ObservationFixtures.clear(jdbc, "TST_PUB");
        java.util.UUID run = ObservationFixtures.run(jdbc, "TST_PUB");
        ObservationFixtures.station(jdbc, "TST_PUB", "PLUV-PUB", -8.0510, -34.8800);
        ObservationFixtures.station(jdbc, "TST_PUB", "PLUV-LONGE", -8.0900, -34.8800);
        Instant h0 = Instant.now().truncatedTo(ChronoUnit.HOURS);
        ObservationFixtures.precipitation(jdbc, run, "TST_PUB", "PLUV-PUB", h0.minus(1, ChronoUnit.HOURS), 3.0);
        ObservationFixtures.precipitation(jdbc, run, "TST_PUB", "PLUV-PUB", h0, 12.5);
        ObservationFixtures.precipitation(jdbc, run, "TST_PUB", "PLUV-LONGE", h0, 99.0);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req(String qs) {
        return get("/api/public/risk?" + qs).header("X-Forwarded-For", ip());
    }

    @Test
    void modoSombra_semLogin_dizQueOAlertaEstaEmTeste_nuncaSemAlerta() throws Exception {
        alerta(true, "pub-alto", "ALERTA");
        String body = mockMvc.perform(req("lat=-8.0502&lon=-34.8803"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearestPoint.slug").value("pub-alto"))
                .andExpect(jsonPath("$.nearestPoint.level").value("SEM_DADO"))
                .andExpect(jsonPath("$.nearestPoint.reason").value("EM_TESTE"))
                .andExpect(jsonPath("$.inTest").value(true))
                .andExpect(jsonPath("$.experimental").value(true))
                .andExpect(jsonPath("$.meaning").value(org.hamcrest.Matchers.startsWith("O alerta de alagamento ainda está em teste")))
                .andExpect(jsonPath("$.rainLastHourMm").value(12.5))
                .andExpect(jsonPath("$.rainStationId").value("PLUV-PUB"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("max-age=60")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("robability").doesNotContain("riskLevel").doesNotContain("Sem alerta").doesNotContain("seguro\"");
        int d = objectMapper.readTree(body).at("/nearestPoint/distanceM").asInt();
        assertThat(d % 50).as("distância arredondada a 50 m").isZero();
        assertThat(d).isLessThanOrEqualTo(1000);
    }

    @Test
    void foraDoModoSombra_manchetePeloNivel_eSemCalculoViraSemDado() throws Exception {
        // coordenadas diferentes das do outro teste: a resposta pública fica 60 s em cache por célula
        alerta(false, "pub-alto", "ALERTA");
        mockMvc.perform(req("lat=-8.0490&lon=-34.8790"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearestPoint.level").value("ALERTA"))
                .andExpect(jsonPath("$.nearestPoint.worstHorizonHours").value(1))
                .andExpect(jsonPath("$.meaning").value("Alerta de alagamento perto de você na próxima hora."));
        mockMvc.perform(req("lat=-8.0990&lon=-34.9490"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearestPoint.slug").value("pub-sem-previsao"))
                .andExpect(jsonPath("$.nearestPoint.level").value("SEM_DADO"))
                .andExpect(jsonPath("$.meaning").value(org.hamcrest.Matchers.containsString("Isto não quer dizer que está seguro")));
    }

    @Test
    void semPontoEm1km_nearestPointNuloEMensagem() throws Exception {
        mockMvc.perform(req("lat=-8.2000&lon=-34.9000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearestPoint").isEmpty())
                .andExpect(jsonPath("$.meaning").value("Não há ponto monitorado perto deste endereço."));
    }

    @Test
    void foraDaRmr_422_semParametro_400() throws Exception {
        mockMvc.perform(req("lat=-9.0&lon=-34.88")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(req("lat=-8.05&lon=-40.0")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(req("lat=-8.05")).andExpect(status().isBadRequest());
        mockMvc.perform(req("lat=abc&lon=-34.88")).andExpect(status().isBadRequest());
    }

    @Test
    void limiteDeTaxa_a31aRequisicaoEm60sRecebe429() throws Exception {
        String ip = ip();
        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(get("/api/public/risk?lat=-8.05&lon=-34.88").header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/public/risk?lat=-8.05&lon=-34.88").header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        // outro IP não é afetado
        mockMvc.perform(req("lat=-8.05&lon=-34.88")).andExpect(status().isOk());
    }

    @Test
    void restoDaApiContinuaExigindoLogin() throws Exception {
        mockMvc.perform(get("/api/pontos/risk-summary")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/public/outra-coisa")).andExpect(status().is4xxClientError());
    }
}

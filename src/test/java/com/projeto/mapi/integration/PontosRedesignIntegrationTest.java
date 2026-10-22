package com.projeto.mapi.integration;

import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.tide.TabuaMareService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.service.weather.MarineService;
import com.projeto.mapi.service.weather.WeatherService;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 2 do redesign: PATCH de ponto, sugestão de estações, risk-summary e série de previsões. */
class PontosRedesignIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired FloodPointRepository pointRepository;
    @Autowired FloodEventRepository eventRepository;
    @Autowired FloodPredictionRepository predictionRepository;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SensorService sensorService;
    @MockitoBean WeatherService weatherService;
    @MockitoBean TideService tideService;
    @MockitoBean TabuaMareService tabuaMareService;
    @MockitoBean MarineService marineService;
    @MockitoBean DataExportService dataExportService;

    @BeforeEach
    void semeia() {
        when(tideService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(null);
        when(tabuaMareService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(null);
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();
    }

    private int seq = 0;

    private FloodPoint ponto(String slug, boolean ativo) {
        // coordenadas distintas: findLatest cai na busca por coordenada quando o ponto não tem linhas
        return pointRepository.save(FloodPoint.builder().slug(slug).name("Ponto " + slug)
                .latitude(-8.05 - 0.001 * (++seq)).longitude(-34.88).active(ativo).build());
    }

    private void previsao(FloodPoint p, Instant issuedAt, String risk, Double prob, String status, String degradation) {
        jdbc.update("INSERT INTO flood_predictions (\"timestamp\", issued_at, flood_point_id, latitude, longitude, "
                        + "flood_probability, risk_level, status, model_version, model_version_full, horizon_hours, degradation_reason) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                LocalDateTime.ofInstant(issuedAt, java.time.ZoneId.systemDefault()), java.sql.Timestamp.from(issuedAt),
                p.getId(), p.getLatitude(), p.getLongitude(), prob, risk, status,
                "SUCCESS".equals(status) ? "xgb-h3-2026.11.0" : null,
                "SUCCESS".equals(status) ? "xgb-h3-2026.11.0+a1b2c3d" : null,
                "SUCCESS".equals(status) ? 3 : null, degradation);
    }

    // ---------- PATCH /api/pontos/{slug} ----------

    @Test
    void patch_matrizDePerfis() throws Exception {
        ponto("p-patch", true);
        String body = "{\"name\":\"Novo nome\"}";
        for (String t : new String[]{null, userToken(), operadorToken()}) {
            mockMvc.perform(as(patch("/api/pontos/p-patch"), t).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(as(patch("/api/pontos/p-patch"), adminToken()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("p-patch"))
                .andExpect(jsonPath("$.name").value("Novo nome"));
    }

    @Test
    void patch_desativaEditaEstacoesEValida() throws Exception {
        ponto("p-patch2", true);
        String t = adminToken();
        mockMvc.perform(as(patch("/api/pontos/p-patch2"), t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"alertThresholdMm\":42.5,\"pluviometerStationIds\":[\"X1\",\"X2\"],\"description\":\"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sensorConfig.pluviometerStationIds.length()").value(2));
        org.assertj.core.api.Assertions.assertThat(pointRepository.findBySlug("p-patch2").orElseThrow().getAlertThresholdMm()).isEqualTo(42.5);

        mockMvc.perform(as(patch("/api/pontos/p-patch2"), t).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/pontos/p-patch2"), t).contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":10.0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/pontos/nao-existe"), t).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarPonto_soAdmin() throws Exception {
        for (String t : new String[]{null, userToken(), operadorToken()}) {
            mockMvc.perform(as(post("/api/pontos"), t).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"slug\":\"x\",\"name\":\"x\",\"latitude\":-8.0,\"longitude\":-34.9}")).andExpect(status().isForbidden());
        }
    }

    // ---------- station-suggestions ----------

    @Test
    void stationSuggestions_soAdmin_ordenaPorDistanciaEFiltraRaio() throws Exception {
        when(sensorService.getAllLatestData()).thenReturn(List.of(
                SensorResponseDTO.builder().sensorId("APAC-PLUVIO-1").stationName("Longe").latitude(-8.0700).longitude(-34.88)
                        .accumulatedPrecipitation(1.0).source("APAC/Cemaden").timestamp(LocalDateTime.now()).build(),
                SensorResponseDTO.builder().sensorId("ANA-1").stationName("Perto").latitude(-8.0510).longitude(-34.88)
                        .waterLevel(1.2).source("ANA").timestamp(LocalDateTime.now()).build(),
                SensorResponseDTO.builder().sensorId("APAC-PLUVIO-2").stationName("Fora do raio").latitude(-8.2).longitude(-34.88)
                        .accumulatedPrecipitation(0.0).source("APAC").build()));
        String url = "/api/pontos/station-suggestions?lat=-8.05&lon=-34.88&radiusM=3000";
        for (String t : new String[]{null, userToken(), operadorToken()}) {
            mockMvc.perform(as(get(url), t)).andExpect(status().isForbidden());
        }
        mockMvc.perform(as(get(url), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stationId").value("ANA-1"))
                .andExpect(jsonPath("$[0].type").value("RIVER_LEVEL"))
                .andExpect(jsonPath("$[0].source").value("ANA"))
                .andExpect(jsonPath("$[1].stationId").value("APAC-PLUVIO-1"))
                .andExpect(jsonPath("$[1].type").value("PRECIPITATION"))
                .andExpect(jsonPath("$[1].source").value("CEMADEN"));
    }

    @Test
    void stationSuggestions_parametrosInvalidos_400() throws Exception {
        mockMvc.perform(as(get("/api/pontos/station-suggestions?lat=-8.05&lon=-34.88&radiusM=50"), adminToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/pontos/station-suggestions?lat=10&lon=-34.88"), adminToken()))
                .andExpect(status().isBadRequest());
    }

    // ---------- risk-summary ----------

    @Test
    void riskSummary_contaPorNivelEPontoSemPrevisaoEhUnknown() throws Exception {
        Instant agora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        FloodPoint a = ponto("p-a", true), b = ponto("p-b", true), c = ponto("p-c", true), d = ponto("p-d", true);
        ponto("p-inativo", false);
        previsao(a, agora.minusSeconds(600), "LOW", 0.1, "SUCCESS", null);
        previsao(a, agora.minusSeconds(60), "HIGH", 0.9, "SUCCESS", null); // a última vale
        previsao(b, agora.minusSeconds(60), "MEDIUM", 0.6, "SUCCESS", null);
        previsao(c, agora.minusSeconds(60), "UNKNOWN", null, "FAILED", "AI_TIMEOUT");
        // d: sem previsão

        mockMvc.perform(get("/api/pontos/risk-summary")).andExpect(status().isForbidden());
        for (String t : new String[]{userToken(), operadorToken(), adminToken()}) {
            mockMvc.perform(as(get("/api/pontos/risk-summary"), t))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.HIGH").value(1))
                    .andExpect(jsonPath("$.MEDIUM").value(1))
                    .andExpect(jsonPath("$.LOW").value(0))
                    .andExpect(jsonPath("$.UNKNOWN").value(2))
                    .andExpect(jsonPath("$.computedAt").isNotEmpty());
        }
    }

    // ---------- série ----------

    @Test
    void serie_ordenadaComUnknownSemProbabilidadeELastValid() throws Exception {
        Instant agora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        FloodPoint p = ponto("p-serie", true);
        Instant t1 = agora.minus(3, ChronoUnit.HOURS), t2 = agora.minus(2, ChronoUnit.HOURS), t3 = agora.minus(1, ChronoUnit.HOURS);
        previsao(p, t3, "UNKNOWN", null, "FAILED", "AI_TIMEOUT");
        previsao(p, t1, "MEDIUM", 0.55, "SUCCESS", null);
        previsao(p, t2, "HIGH", 0.85, "SUCCESS", null);
        long antes = predictionRepository.count();

        String url = "/api/pontos/p-serie/predictions?from=" + agora.minus(6, ChronoUnit.HOURS) + "&to=" + agora;
        mockMvc.perform(get(url)).andExpect(status().isForbidden());
        mockMvc.perform(as(get(url), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("p-serie"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.items[0].probability").value(0.55))
                .andExpect(jsonPath("$.items[0].modelVersion").value("xgb-h3-2026.11.0"))
                .andExpect(jsonPath("$.items[0].modelValidationStatus").value("NOT_VALIDATED"))
                .andExpect(jsonPath("$.items[1].riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.items[2].riskLevel").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[2].probability").isEmpty())
                .andExpect(jsonPath("$.items[2].degradationReason").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.items[2].lastValidIssuedAt").value(t2.toString()));
        org.assertj.core.api.Assertions.assertThat(predictionRepository.count()).as("a série não grava").isEqualTo(antes);
    }

    @Test
    void serie_janelaInvalida400_slugInexistente404() throws Exception {
        ponto("p-serie2", true);
        Instant agora = Instant.now();
        String t = userToken();
        mockMvc.perform(as(get("/api/pontos/p-serie2/predictions?from=" + agora.minus(8, ChronoUnit.DAYS) + "&to=" + agora), t))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/pontos/p-serie2/predictions?from=" + agora + "&to=" + agora.minusSeconds(5)), t))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/pontos/nao-existe/predictions?from=" + agora.minusSeconds(60) + "&to=" + agora), t))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/pontos/p-serie2/predictions"), t)).andExpect(status().isBadRequest());
    }
}

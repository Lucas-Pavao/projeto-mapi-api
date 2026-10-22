package com.projeto.mapi.integration;

import com.projeto.mapi.model.FloodEvent;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 6: /api/events/timeline e /api/admin/ingestion/runs|sources. */
class TimelineAndIngestionAdminIntegrationTest extends AbstractApiIntegrationTest {

    /** sensor_data e flood_events guardam a hora local de Recife, como as fontes gravam. */
    private static final ZoneId RECIFE = ZoneId.of("America/Recife");


    @Autowired FloodPointRepository pointRepository;
    @Autowired FloodEventRepository eventRepository;
    @Autowired FloodPredictionRepository predictionRepository;
    @Autowired SensorDataRepository sensorRepository;
    @Autowired JdbcTemplate jdbc;

    private LocalDateTime base;

    @BeforeEach
    void semeia() {
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        sensorRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();
        FloodPoint p = pointRepository.save(FloodPoint.builder().slug("p-tl").name("Ponto TL").latitude(-8.05).longitude(-34.88)
                .pluviometerStationIds(Set.of("APAC-PLUVIO-261160-PLUV-TL")).riverLevelStationIds(Set.of("RIO-TL")).build());
        base = LocalDateTime.now(RECIFE).truncatedTo(ChronoUnit.HOURS).minusHours(2);
        // chuva: duas leituras no mesmo balde de 10 min (vale o máximo) e uma em outro balde
        // chuva: somas horárias de observation.precipitation, servidas como vêm (decisão 45)
        // O contêiner é compartilhado: chuva perto de agora gravada por outro teste entraria na contagem de séries.
        jdbc.update("DELETE FROM observation WHERE variable = 'precipitation'");
        ObservationFixtures.clear(jdbc, "TST_TL");
        java.util.UUID run = ObservationFixtures.run(jdbc, "TST_TL");
        Instant h = base.atZone(RECIFE).toInstant().truncatedTo(ChronoUnit.HOURS);
        ObservationFixtures.precipitation(jdbc, run, "TST_TL", "261160-PLUV-TL", h.plus(1, ChronoUnit.HOURS), 5.0);
        ObservationFixtures.precipitation(jdbc, run, "TST_TL", "261160-PLUV-TL", h.plus(2, ChronoUnit.HOURS), 1.5);
        ObservationFixtures.precipitation(jdbc, run, "TST_TL", "PLUV-OUTRO", h.plus(1, ChronoUnit.HOURS), 9.0);
        sensorRepository.save(SensorData.builder().sensorId("RIO-TL").waterLevel(2.0).timestamp(base.plusMinutes(1)).build());
        sensorRepository.save(SensorData.builder().sensorId("RIO-TL").waterLevel(4.0).timestamp(base.plusMinutes(4)).build());
        eventRepository.save(FloodEvent.builder().floodPoint(p).startTime(base.plusMinutes(20)).severity(FloodEvent.Severity.HIGH)
                .confirmedBy("t").build());
        Instant issued = base.atZone(RECIFE).toInstant().plusSeconds(600);
        jdbc.update("INSERT INTO flood_predictions (\"timestamp\", issued_at, flood_point_id, latitude, longitude, flood_probability, "
                        + "risk_level, status, model_version, model_version_full, horizon_hours) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                base.plusMinutes(10), java.sql.Timestamp.from(issued), p.getId(), -8.05, -34.88, 0.7, "MEDIUM", "SUCCESS", "m1", "m1+abc", 3);
    }

    private String url(String extra) {
        Instant from = base.atZone(RECIFE).toInstant().minus(30, ChronoUnit.MINUTES);
        Instant to = base.atZone(RECIFE).toInstant().plus(3, ChronoUnit.HOURS);
        return "/api/events/timeline?from=" + from + "&to=" + to + extra;
    }

    @Test
    void timeline_agregaPassoDe10Min_semGravar() throws Exception {
        mockMvc.perform(get(url(""))).andExpect(status().isForbidden());
        mockMvc.perform(as(get(url("")), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepMinutes").value(10))
                .andExpect(jsonPath("$.rain[?(@.stationId=='261160-PLUV-TL')].points.length()").value(2))
                .andExpect(jsonPath("$.rain[?(@.stationId=='261160-PLUV-TL')].points[0].v").value(5.0))
                .andExpect(jsonPath("$.rain[?(@.stationId=='261160-PLUV-TL')].points[1].v").value(1.5))
                .andExpect(jsonPath("$.rain.length()").value(2))
                .andExpect(jsonPath("$.river[0].points[0].v").value(3.0))
                .andExpect(jsonPath("$.tide.harbor").value("pe02"))
                .andExpect(jsonPath("$.risk[0].slug").value("p-tl"))
                .andExpect(jsonPath("$.risk[0].items.length()").value(1))
                .andExpect(jsonPath("$.risk[0].items[0].riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.floodEvents.length()").value(1))
                .andExpect(jsonPath("$.floodEvents[0].severity").value("HIGH"));
    }

    @Test
    void timeline_slugsRestringemEstacoes_eValida() throws Exception {
        mockMvc.perform(as(get(url("&slugs=p-tl")), operadorToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rain.length()").value(1))
                .andExpect(jsonPath("$.rain[0].stationId").value("261160-PLUV-TL"));
        mockMvc.perform(as(get(url("&slugs=nao-existe")), userToken())).andExpect(status().isBadRequest());
        Instant now = Instant.now();
        mockMvc.perform(as(get("/api/events/timeline?from=" + now.minus(80, ChronoUnit.HOURS) + "&to=" + now), userToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/events/timeline?from=" + now + "&to=" + now.minusSeconds(1)), userToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timeline_aceitaToNoFuturo_soAMareEhPrevista_restoParaEmAgora() throws Exception {
        Instant now = Instant.now();
        java.util.UUID run = ObservationFixtures.run(jdbc, "TST_TL");
        // chuva numa hora futura e extremos de maré (um antes, dois depois de agora)
        ObservationFixtures.precipitation(jdbc, run, "TST_TL", "261160-PLUV-TL", now.truncatedTo(ChronoUnit.HOURS).plus(2, ChronoUnit.HOURS), 33.0);
        ObservationFixtures.tideExtreme(jdbc, run, now.minus(3, ChronoUnit.HOURS), 0.3);
        ObservationFixtures.tideExtreme(jdbc, run, now.plus(3, ChronoUnit.HOURS), 2.3);
        ObservationFixtures.tideExtreme(jdbc, run, now.plus(9, ChronoUnit.HOURS), 0.5);
        try {
            Instant from = now.minus(2, ChronoUnit.HOURS), to = now.plus(10, ChronoUnit.HOURS);
            String body = mockMvc.perform(as(get("/api/events/timeline?from=" + from + "&to=" + to), userToken()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var tree = objectMapper.readTree(body);

            var futureTide = new java.util.ArrayList<Double>();
            for (var pt : tree.at("/tide/points")) {
                Instant t = Instant.parse(pt.get("t").asText());
                assertThat(t).isBefore(to);
                if (t.isAfter(now) && t.isBefore(now.plus(9, ChronoUnit.HOURS)) && !pt.get("v").isNull()) futureTide.add(pt.get("v").asDouble());
            }
            assertThat(futureTide).as("a maré vem prevista no futuro").isNotEmpty().allMatch(v -> v >= 0.3 && v <= 2.3);
            assertThat(tree.at("/tide/points").size()).as("passo de 10 min até o fim da janela").isGreaterThan(12 * 6 - 2);

            for (var series : tree.get("rain")) {
                for (var pt : series.get("points")) assertThat(Instant.parse(pt.get("t").asText())).isBeforeOrEqualTo(now);
            }
            for (var risk : tree.get("risk")) {
                for (var it : risk.get("items")) assertThat(Instant.parse(it.get("issuedAt").asText())).isBeforeOrEqualTo(now);
            }
            assertThat(body).doesNotContain("33.0");
        } finally {
            jdbc.update("DELETE FROM observation WHERE run_id = ?", run);
        }
    }

    @Test
    void timeline_toAlemDeAgoraMais72h_400_eJanelaTotalContinuaLimitadaA72h() throws Exception {
        Instant now = Instant.now();
        String t = userToken();
        mockMvc.perform(as(get("/api/events/timeline?from=" + now.plus(10, ChronoUnit.HOURS) + "&to=" + now.plus(73, ChronoUnit.HOURS)), t))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/events/timeline?from=" + now.plus(5, ChronoUnit.HOURS) + "&to=" + now.plus(71, ChronoUnit.HOURS)), t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rain.length()").value(0)).andExpect(jsonPath("$.risk[0].items.length()").value(0));
        mockMvc.perform(as(get("/api/events/timeline?from=" + now.minus(2, ChronoUnit.HOURS) + "&to=" + now.plus(71, ChronoUnit.HOURS)), t))
                .andExpect(status().isBadRequest());
    }

    // ---------- ingestão ----------

    private void run(String source, String status, int minutesAgo) {
        run(source, "LIVE", status, minutesAgo);
    }

    private void run(String source, String kind, String status, int minutesAgo) {
        jdbc.update("INSERT INTO ingestion_run (id, source, kind, code_version, parser_version, rule_version, started_at, finished_at, status, obs_inserted) "
                        + "VALUES (?,?,?,'t','t','t', now() - (? * interval '1 minute'), now() - (? * interval '1 minute'), ?, 7)",
                UUID.randomUUID(), source, kind, minutesAgo, minutesAgo, status);
    }

    @Test
    void runs_paginadoComFiltros_soAdmin() throws Exception {
        for (int i = 0; i < 5; i++) run("TST_PAG", i == 0 ? "FAILED" : "OK", i);
        for (String t : new String[]{null, userToken(), operadorToken()}) {
            mockMvc.perform(as(get("/api/admin/ingestion/runs"), t)).andExpect(status().isForbidden());
            mockMvc.perform(as(get("/api/admin/ingestion/sources"), t)).andExpect(status().isForbidden());
        }
        String t = adminToken();
        mockMvc.perform(as(get("/api/admin/ingestion/runs?source=TST_PAG&size=2&page=0"), t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
        mockMvc.perform(as(get("/api/admin/ingestion/runs?source=TST_PAG&size=2&page=2"), t))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(as(get("/api/admin/ingestion/runs?source=TST_PAG&status=FAILED"), t))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(as(get("/api/admin/ingestion/runs?size=1000"), t)).andExpect(status().isBadRequest());
    }

    @Test
    void sources_listaTodasAsFontesComStatus() throws Exception {
        run("TST_SRC", "FAILED", 1);
        mockMvc.perform(as(get("/api/admin/ingestion/sources"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source=='TST_SRC')].status").value("FAILING"))
                .andExpect(jsonPath("$[?(@.source=='CKAN_RECIFE')]").exists());
    }

    @Test
    void sources_revalidateNaoEscondeColetaAoVivoComFalha() throws Exception {
        run("TST_LIVE", "LIVE", "FAILED", 30);
        run("TST_LIVE", "REPROCESS", "OK", 1);
        mockMvc.perform(as(get("/api/admin/ingestion/sources"), adminToken()))
                .andExpect(jsonPath("$[?(@.source=='TST_LIVE')].status").value("FAILING"))
                .andExpect(jsonPath("$[?(@.source=='TST_LIVE')].lastSuccessAt").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }
}

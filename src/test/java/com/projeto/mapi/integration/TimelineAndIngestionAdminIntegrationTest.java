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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 6: /api/events/timeline e /api/admin/ingestion/runs|sources. */
class TimelineAndIngestionAdminIntegrationTest extends AbstractApiIntegrationTest {

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
        base = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(2);
        // chuva: duas leituras no mesmo balde de 10 min (vale o máximo) e uma em outro balde
        // chuva: somas horárias de observation.precipitation, servidas como vêm (decisão 45)
        ObservationFixtures.clear(jdbc, "TST_TL");
        java.util.UUID run = ObservationFixtures.run(jdbc, "TST_TL");
        Instant h = base.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.HOURS);
        ObservationFixtures.precipitation(jdbc, run, "TST_TL", "261160-PLUV-TL", h.plus(1, ChronoUnit.HOURS), 5.0);
        ObservationFixtures.precipitation(jdbc, run, "TST_TL", "261160-PLUV-TL", h.plus(2, ChronoUnit.HOURS), 1.5);
        ObservationFixtures.precipitation(jdbc, run, "TST_TL", "PLUV-OUTRO", h.plus(1, ChronoUnit.HOURS), 9.0);
        sensorRepository.save(SensorData.builder().sensorId("RIO-TL").waterLevel(2.0).timestamp(base.plusMinutes(1)).build());
        sensorRepository.save(SensorData.builder().sensorId("RIO-TL").waterLevel(4.0).timestamp(base.plusMinutes(4)).build());
        eventRepository.save(FloodEvent.builder().floodPoint(p).startTime(base.plusMinutes(20)).severity(FloodEvent.Severity.HIGH)
                .confirmedBy("t").build());
        Instant issued = base.atZone(ZoneId.systemDefault()).toInstant().plusSeconds(600);
        jdbc.update("INSERT INTO flood_predictions (\"timestamp\", issued_at, flood_point_id, latitude, longitude, flood_probability, "
                        + "risk_level, status, model_version, model_version_full, horizon_hours) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                base.plusMinutes(10), java.sql.Timestamp.from(issued), p.getId(), -8.05, -34.88, 0.7, "MEDIUM", "SUCCESS", "m1", "m1+abc", 3);
    }

    private String url(String extra) {
        Instant from = base.atZone(ZoneId.systemDefault()).toInstant().minus(30, ChronoUnit.MINUTES);
        Instant to = base.atZone(ZoneId.systemDefault()).toInstant().plus(3, ChronoUnit.HOURS);
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

    // ---------- ingestão ----------

    private void run(String source, String status, int minutesAgo) {
        jdbc.update("INSERT INTO ingestion_run (id, source, kind, code_version, parser_version, rule_version, started_at, finished_at, status, obs_inserted) "
                        + "VALUES (?,?,'LIVE','t','t','t', now() - (? * interval '1 minute'), now() - (? * interval '1 minute'), ?, 7)",
                UUID.randomUUID(), source, minutesAgo, minutesAgo, status);
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
}

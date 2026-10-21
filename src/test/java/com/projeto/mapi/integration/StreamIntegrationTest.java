package com.projeto.mapi.integration;

import com.projeto.mapi.dto.AlertDTO;
import com.projeto.mapi.dto.ModelValidationStatus;
import com.projeto.mapi.dto.PredictionSummaryDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.dto.RiskSummaryDTO;
import com.projeto.mapi.dto.SensorReadingEventDTO;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.service.alert.AlertCreatedEvent;
import com.projeto.mapi.service.flood.PredictionCycleCompletedEvent;
import com.projeto.mapi.service.flood.PredictionQueryService;
import com.projeto.mapi.service.sensor.SensorReadingSavedEvent;
import com.projeto.mapi.service.stream.LiveStreamService;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 5: GET /api/stream (ADR 0012). */
class StreamIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired ApplicationEventPublisher publisher;
    @Autowired LiveStreamService streamService;
    @MockitoBean PredictionQueryService predictionQuery;

    private MvcResult open(String user, Role role, String lastEventId) throws Exception {
        var req = as(get("/api/stream"), bearer(user, role)).accept("text/event-stream");
        if (lastEventId != null) req = req.header("Last-Event-ID", lastEventId);
        return mockMvc.perform(req).andExpect(request().asyncStarted()).andReturn();
    }

    private AlertDTO alerta() {
        return new AlertDTO(1L, 1L, "RISK_LEVEL", "CRITICAL", "p", null, "Risco HIGH", "d", 0.9, "HIGH", null,
                "NOT_VALIDATED", Instant.now(), null, null);
    }

    @Test
    void anonimoNaoAbre() throws Exception {
        mockMvc.perform(get("/api/stream")).andExpect(status().isForbidden());
    }

    @Test
    void alertSoChegaAOperadorEAdmin_sensorChegaATodos() throws Exception {
        MvcResult user = open("sse_user", Role.USER, null);
        MvcResult op = open("sse_op", Role.OPERADOR, null);
        MvcResult admin = open("sse_admin", Role.ADMIN, null);

        publisher.publishEvent(new AlertCreatedEvent(alerta()));
        publisher.publishEvent(new SensorReadingSavedEvent(
                new SensorReadingEventDTO("S1", "PRECIPITATION", 1.5, "mm", Instant.parse("2026-10-21T12:00:00Z"))));

        String u = user.getResponse().getContentAsString();
        String o = op.getResponse().getContentAsString();
        String a = admin.getResponse().getContentAsString();
        assertThat(u).contains("event:sensor").contains("\"sensorId\":\"S1\"").doesNotContain("event:alert");
        assertThat(o).contains("event:alert").contains("event:sensor").contains("\"riskLevel\":\"HIGH\"");
        assertThat(a).contains("event:alert");
        assertThat(u).contains("2026-10-21T12:00:00Z");
    }

    @Test
    void fimDeCicloPublicaPredictionERiskSummary() throws Exception {
        when(predictionQuery.latestPerActivePoint()).thenReturn(List.of(new PredictionSummaryDTO("p-x", RiskLevel.UNKNOWN,
                null, Instant.now(), null, null, null, null, null, ModelValidationStatus.NOT_VALIDATED)));
        when(predictionQuery.riskSummary()).thenReturn(new RiskSummaryDTO(0, 0, 0, 1, Instant.now()));
        MvcResult r = open("sse_cycle", Role.USER, null);

        publisher.publishEvent(new PredictionCycleCompletedEvent("teste", Instant.now()));

        assertThat(r.getResponse().getContentAsString())
                .contains("event:prediction").contains("\"slug\":\"p-x\"").contains("\"riskLevel\":\"UNKNOWN\"")
                .contains("event:risk-summary").contains("\"UNKNOWN\":1");
    }

    @Test
    void quartaConexaoDoMesmoUsuarioRecebe429ComRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) open("sse_limite", Role.USER, null);
        assertThat(streamService.activeConnections()).isGreaterThanOrEqualTo(3);
        mockMvc.perform(as(get("/api/stream"), bearer("sse_limite", Role.USER)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        // outro usuário não é afetado
        open("sse_outro", Role.USER, null);
    }

    @Test
    void lastEventIdReenviaOQueEstaNoBuffer_respeitandoPerfil() throws Exception {
        open("sse_replay_keeper", Role.USER, null); // sem ouvinte, leituras de sensor nem entram no buffer
        publisher.publishEvent(new AlertCreatedEvent(alerta()));
        publisher.publishEvent(new SensorReadingSavedEvent(new SensorReadingEventDTO("S-REPLAY", "RIVER_LEVEL", 2.0, "m", Instant.now())));
        // id 0 = tudo que está no buffer
        MvcResult user = open("sse_replay_u", Role.USER, "0");
        MvcResult op = open("sse_replay_o", Role.OPERADOR, "0");
        assertThat(user.getResponse().getContentAsString()).contains("S-REPLAY").doesNotContain("event:alert");
        assertThat(op.getResponse().getContentAsString()).contains("S-REPLAY").contains("event:alert");
    }

    @Test
    void heartbeatChegaAosAbertos() throws Exception {
        MvcResult r = open("sse_hb", Role.USER, null);
        ((com.projeto.mapi.service.stream.impl.LiveStreamServiceImpl) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(streamService)).heartbeat();
        assertThat(r.getResponse().getContentAsString()).contains("event:heartbeat");
    }
}

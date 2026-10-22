package com.projeto.mapi.integration;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 3: /api/reports. Confirmar cria FloodEvent e nunca toca flood_occurrence. */
class ReportsIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired FloodPointRepository pointRepository;
    @Autowired FloodEventRepository eventRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void semeia() {
        jdbc.update("DELETE FROM flood_report");
        eventRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();
        pointRepository.save(FloodPoint.builder().slug("p-rel").name("Ponto relato")
                .latitude(-8.05).longitude(-34.88).build());
    }

    private String body(double lat, double lon) {
        return "{\"latitude\":" + lat + ",\"longitude\":" + lon + ",\"observedAt\":\""
                + Instant.now().minus(10, ChronoUnit.MINUTES) + "\",\"waterDepth\":\"KNEE\",\"description\":\"rua alagada\"}";
    }

    private long criar(double lat, double lon) throws Exception {
        String r = mockMvc.perform(as(post("/api/reports"), userToken()).contentType(MediaType.APPLICATION_JSON).content(body(lat, lon)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("id").asLong();
    }

    @Test
    void enviar_userOuMais_anonimoNao() throws Exception {
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body(-8.05, -34.88)))
                .andExpect(status().isForbidden());
        for (String t : new String[]{userToken(), operadorToken(), adminToken()}) {
            mockMvc.perform(as(post("/api/reports"), t).contentType(MediaType.APPLICATION_JSON).content(body(-8.0505, -34.88)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.nearestPointSlug").value("p-rel"))
                    .andExpect(jsonPath("$.nearestPointDistanceM").isNumber())
                    .andExpect(jsonPath("$.waterDepth").value("KNEE"));
        }
    }

    @Test
    void enviar_longeDeQualquerPonto_semPontoProximo_eValidacoes() throws Exception {
        mockMvc.perform(as(post("/api/reports"), userToken()).contentType(MediaType.APPLICATION_JSON).content(body(-8.2, -34.9)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nearestPointSlug").isEmpty());
        mockMvc.perform(as(post("/api/reports"), userToken()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        String futuro = "{\"latitude\":-8.05,\"longitude\":-34.88,\"observedAt\":\"" + Instant.now().plus(1, ChronoUnit.HOURS) + "\"}";
        mockMvc.perform(as(post("/api/reports"), userToken()).contentType(MediaType.APPLICATION_JSON).content(futuro))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/reports"), userToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(body(-8.05, -34.88).replace("KNEE", "OCEAN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fila_soOperador() throws Exception {
        criar(-8.05, -34.88);
        for (String t : new String[]{null, userToken()}) {
            mockMvc.perform(as(get("/api/reports"), t)).andExpect(status().isForbidden());
        }
        for (String t : new String[]{operadorToken(), adminToken()}) {
            mockMvc.perform(as(get("/api/reports?status=PENDING"), t))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        }
        mockMvc.perform(as(get("/api/reports?status=CONFIRMED"), operadorToken()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(as(get("/api/reports?status=XXX"), operadorToken())).andExpect(status().isBadRequest());
    }

    @Test
    void confirmar_criaFloodEvent_naoTocaFloodOccurrence_e409NaSegunda() throws Exception {
        long id = criar(-8.05, -34.88);
        String conf = "{\"status\":\"CONFIRMED\",\"floodPointSlug\":\"p-rel\",\"severity\":\"HIGH\",\"note\":\"visto em campo\"}";
        for (String t : new String[]{null, userToken()}) {
            mockMvc.perform(as(patch("/api/reports/" + id), t).contentType(MediaType.APPLICATION_JSON).content(conf))
                    .andExpect(status().isForbidden());
        }
        long ocorrenciasAntes = jdbc.queryForObject("SELECT count(*) FROM flood_occurrence", Long.class);
        long eventosAntes = eventRepository.count();

        mockMvc.perform(as(patch("/api/reports/" + id), operadorToken()).contentType(MediaType.APPLICATION_JSON).content(conf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reviewedBy").value("it_operador"))
                .andExpect(jsonPath("$.floodEventId").isNumber());

        assertThat(eventRepository.count()).isEqualTo(eventosAntes + 1);
        var ev = eventRepository.findAll().get(0);
        assertThat(ev.getConfirmedBy()).isEqualTo("it_operador");
        assertThat(ev.getSeverity().name()).isEqualTo("HIGH");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flood_occurrence", Long.class)).isEqualTo(ocorrenciasAntes);

        mockMvc.perform(as(patch("/api/reports/" + id), adminToken()).contentType(MediaType.APPLICATION_JSON).content(conf))
                .andExpect(status().isConflict());
    }

    @Test
    void descartar_guardaMotivo_eValidaCorpo() throws Exception {
        long id = criar(-8.05, -34.88);
        String t = operadorToken();
        mockMvc.perform(as(patch("/api/reports/" + id), t).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISCARDED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/reports/" + id), t).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\",\"floodPointSlug\":\"p-rel\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/reports/" + id), t).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/reports/" + id), t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISCARDED\",\"reason\":\"duplicado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.discardReason").value("duplicado"));
        assertThat(eventRepository.count()).isZero();
        mockMvc.perform(as(patch("/api/reports/999999"), t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISCARDED\",\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmarComPontoInexistente_404() throws Exception {
        long id = criar(-8.05, -34.88);
        mockMvc.perform(as(patch("/api/reports/" + id), operadorToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\",\"floodPointSlug\":\"nao-existe\",\"severity\":\"LOW\"}"))
                .andExpect(status().isNotFound());
    }
}

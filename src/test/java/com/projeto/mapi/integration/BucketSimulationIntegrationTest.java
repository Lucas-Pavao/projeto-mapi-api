package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 8: POST /api/model/simulate/bucket com a IA simulada por um servidor HTTP local. */
class BucketSimulationIntegrationTest extends AbstractApiIntegrationTest {

    static HttpServer ai;
    static final AtomicReference<String> lastBody = new AtomicReference<>();
    static final AtomicInteger aiStatus = new AtomicInteger(200);
    static final AtomicInteger calls = new AtomicInteger();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        ai = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ai.createContext("/v1/simulate/bucket", ex -> {
            calls.incrementAndGet();
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resp = aiStatus.get() == 200
                    ? "{\"h_mm\":[0.0,20.0,155.0],\"n_fora_da_curva\":0,\"hmax_mm\":155.0,\"t_cross_h\":2,"
                    + "\"params_usados\":{\"C\":0.86,\"iT_mm_h\":60.1,\"f\":1.0,\"hcrit_mm\":150.0,\"snap_m\":10,\"curve_scale\":1.0},"
                    + "\"model\":\"M1a\",\"illustrative\":true}"
                    : "{\"detail\":\"erro\"}";
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(aiStatus.get(), resp.getBytes(StandardCharsets.UTF_8).length);
            ex.getResponseBody().write(resp.getBytes(StandardCharsets.UTF_8));
            ex.close();
        });
        ai.start();
        r.add("app.ai.api-url", () -> "http://127.0.0.1:" + ai.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        ai.stop(0);
    }

    @Autowired FloodPointRepository pointRepository;
    @Autowired FloodEventRepository eventRepository;
    @Autowired FloodPredictionRepository predictionRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void semeia() {
        aiStatus.set(200);
        calls.set(0);
        lastBody.set(null);
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        limparDependentesDePontos();
        jdbc.update("DELETE FROM point_terrain");
        jdbc.update("DELETE FROM terrain_run");
        pointRepository.deleteAll();
        FloodPoint com = pointRepository.save(FloodPoint.builder().slug("p-terreno").name("Com terreno").latitude(-8.05).longitude(-34.88).build());
        pointRepository.save(FloodPoint.builder().slug("p-jaboatao").name("Sem terreno").latitude(-8.11).longitude(-35.0).build());

        Long run = jdbc.queryForObject("INSERT INTO terrain_run (vrt_sha256, script_sha256, gdal_image_digest, software_versions, sheets, parameters, "
                + "flight_year, vertical_reference, current) VALUES (repeat('a',64), repeat('b',64), 'd', '{}'::jsonb, ARRAY['x'], '{}'::jsonb, 2015, 'ref', TRUE) RETURNING id", Long.class);
        Long pt = jdbc.queryForObject("INSERT INTO point_terrain (flood_point_id, run_id, status, street_elevation_m, contributing_area_m2, bottom_elevation_m) "
                + "VALUES (?, ?, 'OK', 5.30, 12000, 5.00) RETURNING id", Long.class, com.getId(), run);
        for (int cm = 0; cm <= 20; cm += 5) {
            jdbc.update("INSERT INTO point_terrain_curve (point_terrain_id, above_bottom_cm, elevation_m, cumulative_volume_m3) VALUES (?,?,?,?)",
                    pt, cm, 5.0 + cm / 100.0, cm * 2.0);
        }
    }

    private String body(String slug, String extra) {
        return "{\"slug\":\"" + slug + "\",\"rainfallMmH\":[0,30,80]" + extra + "}";
    }

    @Test
    void matrizDePerfis_anonimoNao_userEmaisSim() throws Exception {
        mockMvc.perform(post("/api/model/simulate/bucket").contentType(MediaType.APPLICATION_JSON).content(body("p-terreno", "")))
                .andExpect(status().isForbidden());
        for (String t : new String[]{userToken(), operadorToken(), adminToken()}) {
            mockMvc.perform(as(post("/api/model/simulate/bucket"), t).contentType(MediaType.APPLICATION_JSON).content(body("p-terreno", "")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void montaACurvaDoTerrenoEMapeiaARespostaDaIA() throws Exception {
        mockMvc.perform(as(post("/api/model/simulate/bucket"), userToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("p-terreno", ",\"f\":0.4,\"curveScale\":1.2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("M1a"))
                .andExpect(jsonPath("$.illustrative").value(true))
                .andExpect(jsonPath("$.hMm.length()").value(3))
                .andExpect(jsonPath("$.hmaxMm").value(155.0))
                .andExpect(jsonPath("$.tCrossH").value(2))
                .andExpect(jsonPath("$.nOutsideCurve").value(0))
                .andExpect(jsonPath("$.paramsUsed.hcrit_mm").value(150.0))
                .andExpect(jsonPath("$.paramsUsed.snap_m").value(10));

        JsonNode sent = objectMapper.readTree(lastBody.get());
        assertThat(sent.get("P_mm_h")).hasSize(3);
        assertThat(sent.at("/point/slug").asText()).isEqualTo("p-terreno");
        assertThat(sent.at("/point/area_m2").asDouble()).isEqualTo(12000.0);
        assertThat(sent.at("/point/rua_rel_m").asDouble()).isEqualTo(0.30, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(sent.at("/point/z_curva_m")).hasSize(5);
        assertThat(sent.at("/point/z_curva_m/1").asDouble()).isEqualTo(0.05);
        assertThat(sent.at("/point/v_curva_m3/4").asDouble()).isEqualTo(40.0);
        assertThat(sent.at("/params/f").asDouble()).isEqualTo(0.4);
        assertThat(sent.at("/params/curve_scale").asDouble()).isEqualTo(1.2);
        assertThat(sent.at("/params").has("C")).isFalse();
    }

    @Test
    void pontoSemTerreno_422_semChamarAIA() throws Exception {
        mockMvc.perform(as(post("/api/model/simulate/bucket"), userToken()).contentType(MediaType.APPLICATION_JSON).content(body("p-jaboatao", "")))
                .andExpect(status().isUnprocessableEntity());
        assertThat(calls.get()).isZero();
    }

    @Test
    void parametroForaDaFaixa_422_pontoInexistente_404_corpoInvalido_400() throws Exception {
        String t = userToken();
        mockMvc.perform(as(post("/api/model/simulate/bucket"), t).contentType(MediaType.APPLICATION_JSON).content(body("p-terreno", ",\"f\":0.5")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(as(post("/api/model/simulate/bucket"), t).contentType(MediaType.APPLICATION_JSON).content(body("p-terreno", ",\"curveScale\":1.5")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(as(post("/api/model/simulate/bucket"), t).contentType(MediaType.APPLICATION_JSON).content(body("nao-existe", "")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/model/simulate/bucket"), t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"p-terreno\",\"rainfallMmH\":[]}")).andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/model/simulate/bucket"), t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"p-terreno\",\"rainfallMmH\":[301]}")).andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/model/simulate/bucket"), t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"p-terreno\",\"rainfallMmH\":[" + "1,".repeat(72) + "1]}")).andExpect(status().isBadRequest());
        assertThat(calls.get()).isZero();
    }

    @Test
    void iaComErro_503() throws Exception {
        aiStatus.set(500);
        mockMvc.perform(as(post("/api/model/simulate/bucket"), userToken()).contentType(MediaType.APPLICATION_JSON).content(body("p-terreno", "")))
                .andExpect(status().isServiceUnavailable());
    }
}

package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NC-BACK B1 a B4: campos novos da canônica, série horária, candidatas do ponto e grade de checagens. */
class NovosCenariosApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final String ATTR = "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC";
    private static final Instant H12 = Instant.parse("2026-11-08T12:00:00Z");
    private static final Instant H13 = Instant.parse("2026-11-08T13:00:00Z");
    private static final Instant H14 = Instant.parse("2026-11-08T14:00:00Z");

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM station_canonical");
        jdbc.update("DELETE FROM station_month_check WHERE source = 'CEMADEN_PED'");
        jdbc.update("DELETE FROM point_station_link WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM station WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
    }

    private void station(String source, String id, String name, double lat, double lon) {
        ObservationFixtures.station(jdbc, source, id, lat, lon);
        jdbc.update("UPDATE station SET name = ? WHERE source = ? AND station_id = ?", name, source, id);
    }

    private void check(String id, String status, String reasonsArray) {
        jdbc.update("INSERT INTO station_month_check (source, station_id, month_start_utc, status, reasons, hours_in_month, "
                + "hours_with_value, coverage, n_neighbors, rule_version) VALUES ('CEMADEN_PED', ?, '2026-11-01T00:00:00Z', ?, "
                + "?::text[], 720, 700, 0.97, 3, 'chuva-ponto-v4')", id, status, reasonsArray);
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token,
                          int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as(req, token)).andExpect(status().is(expected)).andReturn()
                .getResponse().getContentAsString());
    }

    /** PK1 (PED + ao vivo no mesmo lugar) e duas vizinhas PED a ~1 km. */
    private void estacoes() throws Exception {
        station("CEMADEN_PED", "PK1", "Pico", -8.0, -34.9);
        station("CEMADEN", "LIVE-PK1", "[CEMADEN] Pico", -8.0, -34.9);
        station("CEMADEN_PED", "N1", "Vizinha 1", -8.005, -34.9);
        station("CEMADEN_PED", "N2", "Vizinha 2", -8.0, -34.905);
        mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
    }

    @Test
    void canonical_trazMesQuarentenaFontesEDivergencias() throws Exception {
        estacoes();
        UUID ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        UUID live = ObservationFixtures.run(jdbc, "CEMADEN");
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "PK1", H12, 4.0);
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "LIVE-PK1", H12, 5.0);
        mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
        check("PK1", "PASS", "{}");
        check("N1", "FAIL", "{STUCK_ZERO,UNDERCATCH}");
        UUID raw = UUID.randomUUID();
        jdbc.update("INSERT INTO raw_observation (id, ingested_at, run_id, source, station_id, collected_at, source_ts_text, "
                + "source_ts_norm, source_tz, payload, content_hash) VALUES (?, now(), ?, 'CEMADEN', 'LIVE-PK1', now(), 'x', 'x', "
                + "'UTC', '{}'::jsonb, repeat('c',64))", raw, live);
        jdbc.update("INSERT INTO ingestion_quarantine (run_id, raw_id, raw_ingested_at, rule_id, rule_version, reason, "
                + "quarantined_at) SELECT ?, id, ingested_at, 'GAP', 't', 'AFTER_GAP', now() FROM raw_observation WHERE id = ?",
                live, raw);

        JsonNode all = json(get("/api/stations/canonical"), userToken(), 200);
        JsonNode pk1 = null, n2 = null, n1 = null;
        for (JsonNode c : all) {
            switch (c.get("canonicalId").asText()) {
                case "PK1" -> pk1 = c;
                case "N1" -> n1 = c;
                case "N2" -> n2 = c;
                default -> { }
            }
        }
        assertThat(pk1.get("sources").toString()).isEqualTo("[\"CEMADEN\",\"CEMADEN_PED\"]");
        assertThat(pk1.get("monthCheck").get("status").asText()).isEqualTo("OK");
        assertThat(pk1.get("monthCheck").get("ruleVersion").asText()).isEqualTo("chuva-ponto-v4");
        assertThat(pk1.get("quarantine").get("AFTER_GAP").asInt()).isEqualTo(1);
        assertThat(pk1.get("quarantine").get("GAP").asInt()).isZero();
        assertThat(pk1.get("divergenceCount").asInt()).isEqualTo(1);
        assertThat(pk1.get("attribution").asText()).isEqualTo(ATTR);
        assertThat(n1.get("monthCheck").get("status").asText()).isEqualTo("FAIL");
        assertThat(n1.get("monthCheck").get("reasons").toString()).isEqualTo("[\"STUCK_ZERO\",\"UNDERCATCH\"]");
        assertThat(n2.get("monthCheck").isNull()).isTrue();
        assertThat(n2.get("sources").toString()).isEqualTo("[\"CEMADEN_PED\"]");
        assertThat(n2.get("aliases")).hasSize(1);       // campos antigos preservados
    }

    @Test
    void hourly_horaSemDadoNula_picoIsoladoMarcado_divergencia() throws Exception {
        estacoes();
        UUID ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        UUID live = ObservationFixtures.run(jdbc, "CEMADEN");
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "PK1", H12, 4.0);
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "LIVE-PK1", H12, 5.0);       // diverge 1,0 mm
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "PK1", H13, 12.0);        // pico isolado
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N1", H13, 0.2);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N2", H13, 0.0);

        JsonNode r = json(get("/api/stations/PK1/hourly?from=2026-11-08T11:00:00Z&to=2026-11-08T14:00:00Z"), userToken(), 200);
        assertThat(r.get("attribution").asText()).isEqualTo(ATTR);
        assertThat(r.get("toleranceMm").asDouble()).isEqualTo(0.5);
        JsonNode h = r.get("hours");
        assertThat(h).hasSize(3);
        assertThat(h.get(0).get("valueMm").asDouble()).isEqualTo(4.0);
        assertThat(h.get(0).get("usedSource").asText()).isEqualTo("CEMADEN_PED");
        assertThat(h.get(0).get("liveMm").asDouble()).isEqualTo(5.0);
        assertThat(h.get(0).get("divergent").asBoolean()).isTrue();
        assertThat(h.get(1).get("isolatedPeakRemoved").asBoolean()).isTrue();
        assertThat(h.get(1).get("valueMm").isNull()).isTrue();
        assertThat(h.get(1).get("pedMm").asDouble()).isEqualTo(12.0);
        assertThat(h.get(2).get("valueMm").isNull()).isTrue();                                  // hora sem dado
        assertThat(h.get(2).get("usedSource").isNull()).isTrue();
        assertThat(h.get(2).get("isolatedPeakRemoved").asBoolean()).isFalse();
        assertThat(h.get(2).get("hourEndUtc").asText()).isEqualTo(H14.toString());
    }

    @Test
    void hourly_janelaMaiorQue7DiasDa400_canonicaInexistenteDa404() throws Exception {
        estacoes();
        mockMvc.perform(as(get("/api/stations/PK1/hourly?from=2026-11-01T00:00:00Z&to=2026-11-08T01:00:00Z"), userToken()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/stations/NAO-EXISTE/hourly?from=2026-11-08T00:00:00Z&to=2026-11-08T02:00:00Z"), userToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/stations/PK1/hourly?from=2026-11-01T00:00:00Z&to=2026-11-08T00:00:00Z"), userToken()))
                .andExpect(status().isOk());                                                    // exatamente 7 dias
    }

    @Test
    void rainCandidates_papeisPorEstacao() throws Exception {
        station("CEMADEN_PED", "C1", "Perto", -8.0, -34.9);
        station("CEMADEN_PED", "C2", "Meio", -8.0, -34.9);
        station("CEMADEN_PED", "C3", "Reprovada", -8.0, -34.9);
        station("CEMADEN_PED", "C4", "Sem leitura", -8.0, -34.9);
        jdbc.update("DELETE FROM flood_points WHERE slug = 'NC_PONTO'");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude) VALUES ('nc', 'NC_PONTO', -8.0, -34.9)");
        long point = jdbc.queryForObject("SELECT id FROM flood_points WHERE slug = 'NC_PONTO'", Long.class);
        long cfg = jdbc.queryForObject("SELECT min(id) FROM linkage_config", Long.class);
        String[] ids = {"C1", "C2", "C3", "C4"};
        for (int i = 0; i < ids.length; i++) {
            jdbc.update("INSERT INTO point_station_link (flood_point_id, source, station_id, variable_scope, distance_m, radius_m, "
                    + "linkage_config_id, method, valid_from) VALUES (?, 'CEMADEN_PED', ?, 'precipitation', ?, 5000, ?, 'MAX_VALID', "
                    + "'2026-01-01T00:00:00Z')", point, ids[i], 100.0 * (i + 1), cfg);
        }
        check("C1", "PASS", "{}");
        check("C3", "FAIL", "{OVERCATCH}");
        UUID ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "C1", H13, 3.2);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "C2", H13, 1.0);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "C3", H13, 9.0);
        jdbc.update("DELETE FROM point_rain_hourly WHERE flood_point_id = ?", point);
        jdbc.update("INSERT INTO point_rain_hourly (flood_point_id, hour_end_utc, value, source, station_id, station_distance_m, "
                + "n_candidates, n_valid, n_valid_with_value, linkage_config_id, rule_version) VALUES (?, ?::timestamptz, 3.2, "
                + "'CEMADEN_PED', 'C1', 100, 4, 3, 2, ?, 'chuva-ponto-v4')", point, H13.toString(), cfg);

        for (String url : new String[]{"/api/pontos/NC_PONTO/rain-candidates?at=" + H13, "/api/pontos/NC_PONTO/rain-candidates"}) {
            JsonNode r = json(get(url), userToken(), 200);
            assertThat(r.get("hourEndUtc").asText()).isEqualTo(H13.toString());
            assertThat(r.get("radiusM").asInt()).isEqualTo(5000);
            assertThat(r.get("ruleVersion").asText()).isEqualTo("chuva-ponto-v4");
            assertThat(r.get("valueMm").asDouble()).isEqualTo(3.2);
            assertThat(r.get("usedStationId").asText()).isEqualTo("C1");
            assertThat(r.get("attribution").asText()).isEqualTo(ATTR);
            JsonNode c = r.get("candidates");
            assertThat(c).hasSize(4);
            assertThat(c.get(0).get("role").asText()).isEqualTo("USED");
            assertThat(c.get(0).get("monthStatus").asText()).isEqualTo("OK");
            assertThat(c.get(1).get("role").asText()).isEqualTo("LOWER");
            assertThat(c.get(2).get("role").asText()).isEqualTo("EXCLUDED");
            assertThat(c.get(2).get("reasons").toString()).isEqualTo("[\"OVERCATCH\"]");
            assertThat(c.get(3).get("role").asText()).isEqualTo("NO_VALUE");
            assertThat(c.get(3).get("valueMm").isNull()).isTrue();
        }
        mockMvc.perform(as(get("/api/pontos/NAO_EXISTE/rain-candidates"), userToken())).andExpect(status().isNotFound());
    }

    @Test
    void monthChecks_gradeSoParaAdmin() throws Exception {
        station("CEMADEN_PED", "G1", "Grade", -8.0, -34.9);
        check("G1", "PASS", "{}");
        mockMvc.perform(as(get("/api/admin/stations/month-checks"), userToken())).andExpect(status().isForbidden());
        JsonNode r = json(get("/api/admin/stations/month-checks?from=2026-09-01T00:00:00Z&to=2026-12-01T00:00:00Z"),
                adminToken(), 200);
        assertThat(r.get("ruleVersion").asText()).isEqualTo("chuva-ponto-v4");
        assertThat(r.get("months")).hasSize(3);
        assertThat(r.get("months").get(0).asText()).isEqualTo("2026-09-01T00:00:00Z");
        JsonNode st = r.get("stations").get(0);
        assertThat(st.get("stationId").asText()).isEqualTo("G1");
        assertThat(st.get("name").asText()).isEqualTo("Grade");
        assertThat(st.get("cells")).hasSize(1);
        assertThat(st.get("cells").get(0).get("status").asText()).isEqualTo("OK");
        mockMvc.perform(as(get("/api/admin/stations/month-checks?from=2020-01-01T00:00:00Z&to=2026-12-01T00:00:00Z"), adminToken()))
                .andExpect(status().isBadRequest());
    }
}

package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V25 e ADR 0023: a mesma estação vinda da coleta ao vivo (CEMADEN) e da PED vira uma canônica; na mesma hora fica a
 * PED e a diferença acima de 0,5 mm é registrada; o acumulado do ponto com lacuna é nulo, nunca zero.
 */
class StationUnificationIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant H1 = Instant.parse("2026-11-08T12:00:00Z");

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM station_canonical");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM point_station_link WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM station WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
    }

    private void named(String source, String id, String name, double lat, double lon) {
        ObservationFixtures.station(jdbc, source, id, lat, lon);
        jdbc.update("UPDATE station SET name = ?, municipality = 'RECIFE' WHERE source = ? AND station_id = ?", name, source, id);
    }

    @Test
    void unificaPorCoordenada_preferePed_eRegistraDivergencia() throws Exception {
        named("CEMADEN_PED", "261160610A", "Alto Mandu", -8.019, -34.931);
        named("CEMADEN", "261160610A", "[CEMADEN] Alto Mandu", -8.019, -34.931);      // mesmo código
        named("CEMADEN_PED", "261160603A", "Ibura", -8.120, -34.940);
        named("CEMADEN", "APAC-IBURA", "[APAC] Escola do Ibura", -8.1205, -34.940);   // ~55 m, código diferente
        named("CEMADEN", "261160699A", "[CEMADEN] Longe", -8.200, -34.940);           // só ao vivo
        named("CEMADEN_PED", "261160601A", "Só PED", -8.050, -34.900);

        UUID ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        UUID live = ObservationFixtures.run(jdbc, "CEMADEN");
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "261160610A", H1, 10.0);
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "261160610A", H1, 8.0);      // diverge 2,0 mm
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "261160610A", H1.plus(Duration.ofHours(1)), 1.0);
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "261160610A", H1.plus(Duration.ofHours(1)), 1.2); // tolerado
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "261160610A", H1.plus(Duration.ofHours(2)), 3.0); // só ao vivo

        JsonNode report = objectMapper.readTree(mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(report.get("canonicalStations").asInt()).isEqualTo(4);
        assertThat(report.get("unified").asInt()).isEqualTo(2);
        assertThat(report.get("unifiedSameCode").asInt()).isEqualTo(1);
        assertThat(report.get("pedOnly").asInt()).isEqualTo(1);
        assertThat(report.get("liveOnly").asInt()).isEqualTo(1);
        assertThat(report.get("hoursCompared").asInt()).isEqualTo(2);
        assertThat(report.get("divergences").asInt()).isEqualTo(1);

        Map<String, Object> div = jdbc.queryForMap("SELECT kept_source, kept_value, other_value FROM station_hour_divergence");
        assertThat(div.get("kept_source")).isEqualTo("CEMADEN_PED");
        assertThat(div.get("kept_value")).isEqualTo(10.0);

        // série canônica: PED nas horas com as duas, ao vivo onde só ela tem valor
        assertThat(jdbc.queryForObject("SELECT source FROM canonical_rain_hourly_v WHERE canonical_id = '261160610A' "
                + "AND hour_end_utc = ?::timestamptz", String.class, H1.toString())).isEqualTo("CEMADEN_PED");
        assertThat(jdbc.queryForObject("SELECT value FROM canonical_rain_hourly_v WHERE canonical_id = '261160610A' "
                + "AND hour_end_utc = ?::timestamptz", Double.class, H1.plus(Duration.ofHours(2)).toString())).isEqualTo(3.0);

        JsonNode canon = objectMapper.readTree(mockMvc.perform(as(get("/api/stations/canonical"), userToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode ibura = null;
        for (JsonNode c : canon) if (c.get("canonicalId").asText().equals("261160603A")) ibura = c;
        assertThat(ibura).isNotNull();
        assertThat(ibura.get("ibgeCode").asText()).isEqualTo("2611606");
        assertThat(ibura.get("aliases").toString()).contains("CEMADEN:APAC-IBURA", "CEMADEN_PED:261160603A");

        // repetir não duplica
        mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM station_alias", Integer.class)).isEqualTo(6);
    }

    @Test
    void unificarExigeAdmin() throws Exception {
        mockMvc.perform(as(post("/api/admin/stations/unify"), userToken())).andExpect(status().isForbidden());
    }

    @Test
    void acumuladoDoPonto_lacunaDaNulo_naoZero() {
        jdbc.update("""
                INSERT INTO flood_points (name, slug, latitude, longitude) SELECT 'acc', 'ACC_TESTE', -8.0, -34.9
                WHERE NOT EXISTS (SELECT 1 FROM flood_points WHERE slug = 'ACC_TESTE')""");
        long point = jdbc.queryForObject("SELECT id FROM flood_points WHERE slug = 'ACC_TESTE'", Long.class);
        long cfg = jdbc.queryForObject("SELECT min(id) FROM linkage_config", Long.class);
        jdbc.update("DELETE FROM point_rain_hourly WHERE flood_point_id = ?", point);
        Double[] values = {1.0, 2.0, 3.0, null, 4.0, 5.0, 6.0};
        for (int i = 0; i < values.length; i++) {
            jdbc.update("""
                    INSERT INTO point_rain_hourly (flood_point_id, hour_end_utc, value, source, n_candidates, n_valid,
                           n_valid_with_value, linkage_config_id, rule_version) VALUES (?, ?::timestamptz, ?, 'CEMADEN_PED', 1, 1, 1, ?, 't')
                    """, point, H1.plus(Duration.ofHours(i)).toString(), values[i], cfg);
        }
        java.util.List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT rain_3h_mm, rain_3h_n FROM point_rain_accum_v WHERE flood_point_id = ? AND rule_version = 't' "
                        + "ORDER BY hour_end_utc", point);
        assertThat(rows.get(2).get("rain_3h_mm")).isEqualTo(6.0);          // 1 + 2 + 3
        assertThat(rows.get(3).get("rain_3h_mm")).isNull();                // lacuna na própria hora
        assertThat(rows.get(5).get("rain_3h_mm")).isNull();                // lacuna dentro da janela
        assertThat(((Number) rows.get(5).get("rain_3h_n")).intValue()).isEqualTo(2);
        assertThat(rows.get(6).get("rain_3h_mm")).isEqualTo(15.0);         // 4 + 5 + 6
    }

    @Test
    void acumuladosMedidos_semCredencial_estacoesFicamSemDado() throws Exception {
        named("CEMADEN_PED", "261160610A", "Alto Mandu", -8.019, -34.931);
        mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
        JsonNode r = objectMapper.readTree(mockMvc.perform(as(post("/api/admin/stations/rain-accumulations/refresh"),
                adminToken())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(r.get("municipalitiesFailed").toString()).contains("2611606");
        assertThat(r.get("attribution").asText()).isEqualTo("DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC");
        JsonNode st = r.get("stations").get(0);
        assertThat(st.get("status").asText()).isEqualTo("NO_DATA");
        assertThat(st.get("acc3hMm").isNull()).isTrue();
        mockMvc.perform(as(get("/api/stations/rain-accumulations"), userToken())).andExpect(status().isOk());
    }
}

package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NC-BACK B6: card do pluviômetro (estados, acumulados nulos, quarentena do dia, pontos atendidos). */
class StationCardApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final String ATTR = "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC";
    private static final Instant AT = Instant.parse("2026-11-08T14:00:00Z");
    private static final Instant START = AT.minusSeconds(3 * 3600);

    @Autowired JdbcTemplate jdbc;
    private UUID ped, live;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("DELETE FROM station_canonical");
        jdbc.update("DELETE FROM station_month_check WHERE source = 'CEMADEN_PED'");
        jdbc.update("DELETE FROM point_station_link WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM station WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        station("CEMADEN_PED", "PK1", "Pico", -8.0, -34.9);
        station("CEMADEN", "LIVE-PK1", "[CEMADEN] Pico", -8.0, -34.9);
        station("CEMADEN_PED", "N1", "Vizinha 1", -8.005, -34.9);
        station("CEMADEN_PED", "N2", "Vizinha 2", -8.0, -34.905);
        mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
        ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        live = ObservationFixtures.run(jdbc, "CEMADEN");
        check("PK1", "PASS", "{}");
    }

    private void station(String source, String id, String name, double lat, double lon) {
        ObservationFixtures.station(jdbc, source, id, lat, lon);
        jdbc.update("UPDATE station SET name = ? WHERE source = ? AND station_id = ?", name, source, id);
    }

    private void check(String id, String status, String reasons) {
        jdbc.update("DELETE FROM station_month_check WHERE source = 'CEMADEN_PED' AND station_id = ?", id);
        jdbc.update("INSERT INTO station_month_check (source, station_id, month_start_utc, status, reasons, hours_in_month, "
                + "hours_with_value, coverage, n_neighbors, rule_version) VALUES ('CEMADEN_PED', ?, '2026-11-01T00:00:00Z', ?, "
                + "?::text[], 720, 700, 0.97, 3, 'chuva-ponto-v4')", id, status, reasons);
    }

    /** Blocos de 10 min com fim em (from, to]; {@code peakAt} recebe 12 mm em vez do valor base. */
    private void blocks(UUID run, String source, String id, Instant from, Instant to, double mm, Instant peakAt) {
        for (Instant t = from.plusSeconds(600); !t.isAfter(to); t = t.plusSeconds(600)) {
            ObservationFixtures.precipitation(jdbc, run, source, id, "PT10M", t, t.equals(peakAt) ? 12.0 : mm, "ACTIVE");
        }
    }

    private JsonNode card(String id, int expected) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/stations/" + id + "/card?at=" + AT);
        return objectMapper.readTree(mockMvc.perform(as(req, userToken())).andExpect(status().is(expected)).andReturn()
                .getResponse().getContentAsString());
    }

    @Test
    void normal_blocosAcumuladosQuarentenaDoDiaEUsos() throws Exception {
        blocks(ped, "CEMADEN_PED", "PK1", START, AT, 0.1, null);
        for (String[] q : new String[][]{{"2026-11-08T10:00:00Z", "x", "c"}, {"2026-11-07T10:00:00Z", "y", "d"}}) {
            UUID raw = UUID.randomUUID();
            jdbc.update("INSERT INTO raw_observation (id, ingested_at, run_id, source, station_id, collected_at, source_ts_text, "
                    + "source_ts_norm, source_tz, payload, content_hash) VALUES (?, now(), ?, 'CEMADEN', 'LIVE-PK1', now(), ?, ?, "
                    + "'UTC', '{}'::jsonb, repeat(?,64))", raw, live, q[1], q[1], q[2]);
            jdbc.update("INSERT INTO ingestion_quarantine (run_id, raw_id, raw_ingested_at, rule_id, rule_version, reason, "
                    + "quarantined_at) SELECT ?, id, ingested_at, 'GAP', 't', 'AFTER_GAP', ?::timestamptz FROM raw_observation WHERE id = ?",
                    live, q[0], raw);
        }
        jdbc.update("DELETE FROM flood_points WHERE slug = 'CARD_PONTO'");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Ponto do card', 'CARD_PONTO', -8.0, -34.9, true)");
        long point = jdbc.queryForObject("SELECT id FROM flood_points WHERE slug = 'CARD_PONTO'", Long.class);
        long cfg = jdbc.queryForObject("SELECT min(id) FROM linkage_config", Long.class);
        String[] ids = {"PK1", "N1"};
        for (int i = 0; i < ids.length; i++) {
            jdbc.update("INSERT INTO point_station_link (flood_point_id, source, station_id, variable_scope, distance_m, radius_m, "
                    + "linkage_config_id, method, valid_from) VALUES (?, 'CEMADEN_PED', ?, 'precipitation', ?, 5000, ?, 'MAX_VALID', "
                    + "'2026-01-01T00:00:00Z')", point, ids[i], 100.0 * (i + 1), cfg);
        }
        jdbc.update("INSERT INTO point_rain_hourly (flood_point_id, hour_end_utc, value, source, station_id, station_distance_m, "
                + "n_candidates, n_valid, n_valid_with_value, linkage_config_id, rule_version) VALUES (?, ?::timestamptz, 0.6, "
                + "'CEMADEN_PED', 'PK1', 100, 2, 2, 1, ?, 'chuva-ponto-v4')", point, AT.toString(), cfg);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N1", AT, 0.2);

        JsonNode c = card("PK1", 200);
        assertThat(c.get("state").asText()).isEqualTo("NORMAL");
        assertThat(c.get("note").isNull()).isTrue();
        assertThat(c.get("sources").toString()).isEqualTo("[\"CEMADEN\",\"CEMADEN_PED\"]");
        assertThat(c.get("pedOnly").asBoolean()).isFalse();
        assertThat(c.get("blocks")).hasSize(18);
        assertThat(c.get("blocks").get(17).get("endUtc").asText()).isEqualTo(AT.toString());
        assertThat(c.get("blocks").get(17).get("source").asText()).isEqualTo("CEMADEN_PED");
        assertThat(c.get("acc1hMm").asDouble()).isEqualTo(0.6);
        assertThat(c.get("acc3hMm").asDouble()).isEqualTo(1.8);
        assertThat(c.get("acc24hMm").isNull()).isTrue();                       // faltam horas: incompleto, não zero
        assertThat(c.get("nowMmH").asDouble()).isCloseTo(0.6, within(1e-9));
        assertThat(c.get("quarantineToday").get("AFTER_GAP").asInt()).isEqualTo(1);
        assertThat(c.get("quarantineToday").get("GAP").asInt()).isZero();
        assertThat(c.get("monthCheck").get("status").asText()).isEqualTo("OK");
        assertThat(c.get("lastReadingUtc").asText()).isEqualTo(AT.toString());
        assertThat(c.get("attribution").asText()).isEqualTo(ATTR);
        assertThat(c.get("uses")).hasSize(1);
        assertThat(c.get("uses").get(0).get("slug").asText()).isEqualTo("CARD_PONTO");
        assertThat(c.get("uses").get(0).get("role").asText()).isEqualTo("USED");

        JsonNode n1 = card("N1", 200);
        assertThat(n1.get("uses").get(0).get("role").asText()).isEqualTo("LOWER");
        assertThat(n1.get("uses").get(0).get("usedStationName").asText()).isEqualTo("Pico");
        assertThat(n1.get("state").asText()).isEqualTo("UNCHECKED");          // mês sem checagem
        assertThat(n1.get("blocks").get(17).get("mm").asDouble()).isEqualTo(0.2); // a PT1H de 14:00 cai no balde do carimbo
        assertThat(n1.get("blocks").get(16).get("mm").asDouble()).isZero();       // os anteriores do bloco contam 0
    }

    @Test
    void gapNow_blocoAtualSemDado_acumuladosNulos() throws Exception {
        blocks(ped, "CEMADEN_PED", "PK1", START, AT.minusSeconds(600), 0.1, null);
        JsonNode c = card("PK1", 200);
        assertThat(c.get("state").asText()).isEqualTo("GAP_NOW");
        assertThat(c.get("blocks").get(17).get("mm").isNull()).isTrue();
        assertThat(c.get("blocks").get(17).get("source").isNull()).isTrue();
        assertThat(c.get("acc1hMm").isNull()).isTrue();
        assertThat(c.get("acc3hMm").isNull()).isTrue();
        assertThat(c.get("note").get("kind").asText()).isEqualTo("GAP_NOW");
        assertThat(c.get("note").get("blockEndUtc").asText()).isEqualTo(AT.toString());
    }

    @Test
    void failMonth_ganhaDeDivergencia() throws Exception {
        check("PK1", "FAIL", "{OVERCATCH}");
        blocks(ped, "CEMADEN_PED", "PK1", START, AT, 0.1, null);
        blocks(live, "CEMADEN", "LIVE-PK1", AT.minusSeconds(3600), AT, 0.2, null);   // diverge também; FAIL_MONTH vem antes
        JsonNode c = card("PK1", 200);
        assertThat(c.get("state").asText()).isEqualTo("FAIL_MONTH");
        assertThat(c.get("note").get("reasons").toString()).isEqualTo("[\"OVERCATCH\"]");
        assertThat(c.get("monthCheck").get("status").asText()).isEqualTo("FAIL");
    }

    @Test
    void divergent_trazOsNumerosDaHora() throws Exception {
        blocks(ped, "CEMADEN_PED", "PK1", START, AT, 0.1, null);
        blocks(live, "CEMADEN", "LIVE-PK1", AT.minusSeconds(3600), AT, 0.2, null);
        JsonNode c = card("PK1", 200);
        assertThat(c.get("state").asText()).isEqualTo("DIVERGENT");
        JsonNode n = c.get("note");
        assertThat(n.get("hourEndUtc").asText()).isEqualTo(AT.toString());
        assertThat(n.get("pedMm").asDouble()).isCloseTo(0.6, within(1e-9));
        assertThat(n.get("liveMm").asDouble()).isCloseTo(1.2, within(1e-9));
        assertThat(n.get("diffMm").asDouble()).isEqualTo(0.6);
        assertThat(c.get("pedMmH").asDouble()).isCloseTo(0.6, within(1e-9));
        assertThat(c.get("liveMmH").asDouble()).isCloseTo(1.2, within(1e-9));
    }

    @Test
    void peakRemoved_picoIsoladoSaiDosBlocos() throws Exception {
        Instant peak = Instant.parse("2026-11-08T13:00:00Z");
        blocks(ped, "CEMADEN_PED", "PK1", START, AT, 0.1, peak);
        Instant h13 = peak;
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N1", h13, 0.2);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N2", h13, 0.0);
        JsonNode c = card("PK1", 200);
        assertThat(c.get("state").asText()).isEqualTo("PEAK_REMOVED");
        assertThat(c.get("note").get("hourEndUtc").asText()).isEqualTo(h13.toString());
        assertThat(c.get("note").get("valueMm").asDouble()).isCloseTo(12.5, within(1e-9));
        JsonNode b = c.get("blocks").get(11);                                    // 13:00
        assertThat(b.get("endUtc").asText()).isEqualTo(h13.toString());
        assertThat(b.get("peakRemoved").asBoolean()).isTrue();
        assertThat(b.get("mm").isNull()).isTrue();
        assertThat(c.get("acc3hMm").isNull()).isTrue();
    }

    @Test
    void canonicaInexistente_da404() throws Exception {
        card("NAO-EXISTE", 404);
    }
}

package com.projeto.mapi.integration;

import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.flood.PointRainInputs;
import com.projeto.mapi.service.flood.impl.PointRainInputServiceImpl;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dia real de chuva (2026-10-28, PED; DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC): o que a previsão ao vivo monta
 * tem de bater, hora a hora e ponto a ponto, com {@code point_rain_accum_v} (ADR 0007, emenda 3). Extraído do banco de
 * desenvolvimento em 2026-11-16 (46 estações, leituras de 2026-10-26 22:00 a 2026-10-29 00:00 UTC).
 */
class PointRainInputRealDayIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant FROM = Instant.parse("2026-10-27T00:00:00Z");
    private static final Instant DAY = Instant.parse("2026-10-28T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-29T00:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired PointRainInputServiceImpl service;
    @Autowired PointRainService pointRain;
    @Autowired com.projeto.mapi.service.flood.PointPhysicalInputService physical;

    private List<String[]> csv(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/ped-2026-10-28/" + name)) {
            List<String[]> rows = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) rows.add(line.trim().split(","));
            }
            return rows;
        }
    }

    @Test
    void previsaoAoVivoBateComAViewDoTreino_horaAHora() throws Exception {
        jdbc.update("DELETE FROM station_canonical");
        jdbc.update("DELETE FROM station_month_check WHERE source = 'CEMADEN_PED'");
        jdbc.update("DELETE FROM point_station_link WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM point_rain_hourly");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM station WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        for (String[] s : csv("station.csv")) {
            ObservationFixtures.station(jdbc, "CEMADEN_PED", s[0], Double.parseDouble(s[1]), Double.parseDouble(s[2]));
        }
        UUID run = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        List<Object[]> batch = new ArrayList<>();
        for (String[] o : csv("observation.csv")) {
            batch.add(new Object[]{o[0], Timestamp.from(Instant.parse(o[1])), Double.parseDouble(o[2]),
                    Timestamp.from(Instant.parse(o[3])), run, o[1]});
        }
        jdbc.batchUpdate("INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, "
                + "interval_start_utc, run_id, raw_hash, rule_version, value_hash, origin_ts_norm) VALUES ('CEMADEN_PED', ?, "
                + "'precipitation', 'EVENT', ?, ?, 'mm', ?, ?, repeat('a',64), 't', repeat('b',64), ?)", batch);
        // Só os pontos do extrato ficam ativos durante o teste; o estado anterior volta no fim.
        List<Long> wasActive = jdbc.queryForList("SELECT id FROM flood_points WHERE active", Long.class);
        jdbc.update("UPDATE flood_points SET active = false");
        try {
            List<String[]> points = csv("points.csv");
            for (String[] p : points) {
                jdbc.update("DELETE FROM flood_points WHERE slug = ?", "FX_" + p[0]);
                jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES (?, ?, ?, ?, true)",
                        "FX " + p[0], "FX_" + p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            }
            mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
            pointRain.rebuild(FROM, TO);

            int compared = 0, wet = 0, withPeak = 0;
            double sum = 0;
            for (Instant h = DAY.plus(Duration.ofHours(1)); !h.isAfter(TO); h = h.plus(Duration.ofHours(1))) {
                service.invalidate();
                for (String[] p : points) {
                    PointRainInputs live = service.inputsAt(Double.parseDouble(p[1]), Double.parseDouble(p[2]), h.plus(Duration.ofMinutes(40)));
                    Map<String, Object> v = jdbc.queryForMap("SELECT rain_1h_mm, rain_3h_mm, rain_6h_mm, rain_12h_mm, rain_24h_mm "
                            + "FROM point_rain_accum_v WHERE slug = ? AND rule_version = 'chuva-ponto-v4' AND hour_end_utc = ?::timestamptz",
                            "FX_" + p[0], h.toString());
                    String where = p[0] + " " + h;
                    assertThat(live.hourEndUtc()).as(where).isEqualTo(h);
                    same(live.current(), v.get("rain_1h_mm"), where + " 1h");
                    same(live.acc3h(), v.get("rain_3h_mm"), where + " 3h");
                    same(live.acc6h(), v.get("rain_6h_mm"), where + " 6h");
                    same(live.acc12h(), v.get("rain_12h_mm"), where + " 12h");
                    same(live.acc24h(), v.get("rain_24h_mm"), where + " 24h");
                    // V27: o pico de 10 min da previsão ao vivo é o da view, e nunca passa da chuva da hora
                    Map<String, Object> x = jdbc.queryForMap("SELECT rain_max_10min_mm FROM point_physical_inputs_v WHERE slug = ? "
                            + "AND rule_version = 'chuva-ponto-v4' AND hour_end_utc = ?::timestamptz", "FX_" + p[0], h.toString());
                    Double max10 = physical.inputsAt(Double.parseDouble(p[1]), Double.parseDouble(p[2]), live).rainMax10minMm();
                    same(max10, x.get("rain_max_10min_mm"), where + " pico 10 min");
                    if (max10 != null) {
                        withPeak++;
                        assertThat(max10).as(where).isLessThanOrEqualTo(live.current() + 0.011);
                    }
                    compared += 5;
                    if (live.current() != null && live.current() > 0) wet++;
                    if (live.current() != null) sum += live.current();
                }
            }
            assertThat(compared).isEqualTo(24 * 5 * 5);
            assertThat(wet).as("horas-ponto com chuva no dia").isGreaterThan(10);   // o dia tem chuva de verdade
            assertThat(sum).isGreaterThan(20.0);
            assertThat(withPeak).as("horas-ponto com pico de 10 min conhecido").isGreaterThan(60);
    
        } finally {
            jdbc.update("DELETE FROM point_rain_hourly WHERE flood_point_id IN (SELECT id FROM flood_points WHERE slug LIKE 'FX\\_%')");
            jdbc.update("DELETE FROM point_station_link WHERE flood_point_id IN (SELECT id FROM flood_points WHERE slug LIKE 'FX\\_%')");
            jdbc.update("DELETE FROM flood_points WHERE slug LIKE 'FX\\_%'");
            for (Long id : wasActive) jdbc.update("UPDATE flood_points SET active = true WHERE id = ?", id);
        }
    }

    private static void same(Double live, Object view, String where) {
        if (view == null) assertThat(live).as(where).isNull();
        else assertThat(live).as(where).isNotNull().isCloseTo(((Number) view).doubleValue(), org.assertj.core.api.Assertions.within(0.011));
    }
}

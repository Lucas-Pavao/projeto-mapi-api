package com.projeto.mapi.integration;

import com.projeto.mapi.controller.PointRainController;
import com.projeto.mapi.dto.PointMeasuredRainDTO;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Chuva medida acumulada no ponto (onda 16, Fase 7.3): point_rain_accum_v, regra chuva-ponto-v4, lacuna nunca vira zero. */
class PointMeasuredRainIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired PointRainController controller;
    @Autowired JdbcTemplate jdbc;

    final Instant h0 = Instant.now().truncatedTo(ChronoUnit.HOURS);
    long pid;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM point_rain_hourly WHERE flood_point_id IN (SELECT id FROM flood_points WHERE slug = 'CHUVA_MEDIDA')");
        jdbc.update("DELETE FROM flood_points WHERE slug = 'CHUVA_MEDIDA'");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Chuva medida', 'CHUVA_MEDIDA', -8.05, -34.95, true)");
        pid = jdbc.queryForObject("SELECT id FROM flood_points WHERE slug = 'CHUVA_MEDIDA'", Long.class);
        jdbc.update("INSERT INTO linkage_config (kind, radius_m, method, created_by) SELECT 'SENSOR_POINT', 5000, 'MAX_VALID_MONTHLY', "
                + "'test-chuva' WHERE NOT EXISTS (SELECT 1 FROM linkage_config WHERE created_by = 'test-chuva')");
    }

    void hour(int hoursAgo, Double mm) {
        long cfg = jdbc.queryForObject("SELECT min(id) FROM linkage_config WHERE created_by = 'test-chuva'", Long.class);
        jdbc.update("INSERT INTO point_rain_hourly (flood_point_id, hour_end_utc, value, source, station_id, station_distance_m, "
                + "n_candidates, n_valid, n_valid_with_value, linkage_config_id, rule_version) VALUES (?, ?, ?, 'CEMADEN_PED', "
                + "'261160619A', 3483.0, 2, 2, ?, ?, 'chuva-ponto-v4')", pid, Timestamp.from(h0.minus(Duration.ofHours(hoursAgo))), mm,
                mm == null ? 0 : 1, cfg);
    }

    @Test
    void acumuladosDaUltimaHoraFechadaELacunaNuncaViraZero() {
        for (int k = 23; k >= 0; k--) hour(k, k == 10 ? null : (k < 3 ? 2.0 : 0.5));
        PointMeasuredRainDTO d = controller.measured("CHUVA_MEDIDA").getBody();
        assertThat(d.hourEndUtc()).isEqualTo(h0);
        assertThat(d.rain1hMm()).isEqualTo(2.0);
        assertThat(d.rain3hMm()).isEqualTo(6.0);
        assertThat(d.rain6hMm()).isEqualTo(7.5);
        assertThat(d.rain24hMm()).isNull();          // uma hora sem valor na janela de 24 h
        assertThat(d.n24h()).isEqualTo(23);
        assertThat(d.stationId()).isEqualTo("261160619A");
        assertThat(d.ruleVersion()).isEqualTo("chuva-ponto-v4");
        assertThat(d.attribution()).contains("CEMADEN");
    }

    @Test
    void pontoSemSerieDa404() {
        assertThat(controller.measured("CHUVA_MEDIDA").getStatusCode().value()).isEqualTo(404);
    }
}

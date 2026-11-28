package com.projeto.mapi.integration;

import com.projeto.mapi.service.flood.RainForecastCheckService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Conferência semanal do rain_v1 (V43, V44): faixa da persistência pela chuva da estação, semana, trecho e pendência. */
class RainForecastCheckIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired RainForecastCheckService service;
    @Autowired JdbcTemplate jdbc;

    private long pointId;
    private UUID ped;
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);       // antes da quebra de 30/11

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM rain_forecast_check");
        jdbc.update("DELETE FROM rain_forecast");
        jdbc.update("DELETE FROM alert WHERE kind = 'MODEL_CHECK'");
        jdbc.update("DELETE FROM station_alias");
        jdbc.update("DELETE FROM station_canonical");
        ObservationFixtures.clear(jdbc, "CEMADEN_PED");
        jdbc.update("DELETE FROM flood_points WHERE slug = 'CK_PONTO'");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Ponto conferência', 'CK_PONTO', -8.05, -34.95, true)");
        pointId = jdbc.queryForObject("SELECT id FROM flood_points WHERE slug = 'CK_PONTO'", Long.class);
        ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        ObservationFixtures.station(jdbc, "CEMADEN_PED", "CK_ST", -8.055, -34.95);
        jdbc.update("INSERT INTO station_canonical (canonical_id, name, lat, lon, primary_source, n_aliases, rule_version) "
                + "VALUES ('CK_ST', 'CK_ST', -8.055, -34.95, 'CEMADEN_PED', 1, 't')");
        jdbc.update("INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version) VALUES ('CEMADEN_PED', 'CK_ST', 'CK_ST', 0, 't')");
    }

    /** 7 dias, 1 previsão de 1 h a cada 30 min; chuva de 3 mm na hora antes de 1 em cada 3 emissões. */
    private void seed(double modelError) {
        Instant t0 = MONDAY.atStartOfDay(ZoneId.of("America/Recife")).toInstant().plus(Duration.ofHours(1));
        for (int i = 0; i < 7 * 48; i++) {
            Instant t = t0.plus(Duration.ofMinutes(30L * i));
            boolean wet = i % 3 == 0;
            ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "CK_ST", "PT10M", t, wet ? 3.0 : 0.0, "ACTIVE");
            double y = wet ? 3.0 : 0.0;
            double q = y + modelError;
            jdbc.update("""
                    INSERT INTO rain_forecast (flood_point_id, issued_at, horizon_hours, valid_until, q50_mm, q80_mm, q95_mm,
                        prob_media, prob_forte, forte_alert, model_version, model, observed_mm, verified_at, verification_note)
                    VALUES (?, ?, 1, ?, ?, ?, ?, 0, 0, false, 'rain_v1', 'gb_rede', ?, now(), 'MEASURED')
                    """, pointId, java.sql.Timestamp.from(t), java.sql.Timestamp.from(t.plus(Duration.ofHours(1))), q, q + 0.5, q + 2, y);
        }
    }

    @Test
    void semanaETrechoGravados_eModeloRuimAbrePendenciaUmaVez() {
        seed(4.0);
        int n = service.runWeek(MONDAY);
        assertThat(n).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rain_forecast_check WHERE scope = 'SEMANA' AND horizon_hours = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT segment FROM rain_forecast_check WHERE scope = 'TRECHO' AND horizon_hours = 1", String.class))
                .isEqualTo("antes de 2026-11-30");
        assertThat(jdbc.queryForObject("SELECT status FROM rain_forecast_check WHERE scope = 'TRECHO' AND horizon_hours = 1", String.class))
                .isEqualTo("PIORA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert WHERE kind = 'MODEL_CHECK'", Integer.class)).isEqualTo(1);
        service.runWeek(MONDAY);                                               // de novo: atualiza, não duplica pendência
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert WHERE kind = 'MODEL_CHECK'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rain_forecast_check WHERE horizon_hours = 1", Integer.class)).isEqualTo(2);
    }

    @Test
    void modeloBomFicaOk_semPendencia() {
        seed(0.0);
        service.runWeek(MONDAY);
        assertThat(jdbc.queryForObject("SELECT status FROM rain_forecast_check WHERE scope = 'TRECHO' AND horizon_hours = 1", String.class))
                .isEqualTo("OK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert WHERE kind = 'MODEL_CHECK'", Integer.class)).isZero();
    }

    @Test
    void quebraDeSerieDoBestMatchRegistradaParaORainV1() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_series_break WHERE model = 'best_match' AND 'rain_v1' = ANY (affects) "
                + "AND break_at = '2026-11-30T00:00:00Z'", Integer.class)).isEqualTo(1);
    }
}

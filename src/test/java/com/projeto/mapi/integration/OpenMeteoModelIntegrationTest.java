package com.projeto.mapi.integration;

import com.projeto.mapi.service.weather.OpenMeteoModelClient;
import com.projeto.mapi.service.weather.OpenMeteoModelClient.Batch;
import com.projeto.mapi.service.weather.OpenMeteoModelClient.Hour;
import com.projeto.mapi.service.weather.OpenMeteoModelClient.RunMeta;
import com.projeto.mapi.service.weather.OpenMeteoModelService;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Open-Meteo por modelo (V42): emissão separada da validade, rodada repetida não regrava, falha de um modelo isolada. */
class OpenMeteoModelIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired OpenMeteoModelService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OpenMeteoModelClient client;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM open_meteo_model_hour");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) "
                + "SELECT 'Ponto OM', 'OM_PONTO', -8.05, -34.95, true WHERE NOT EXISTS (SELECT 1 FROM flood_points WHERE slug = 'OM_PONTO')");
    }

    private static Batch batch(Instant firstEnd, int n, double mm) {
        List<Hour> hs = new ArrayList<>();
        for (int i = 0; i < n; i++) hs.add(new Hour(firstEnd.plus(Duration.ofHours(i)), i == 3 ? null : mm, 100.0));
        String body = "{\"utc_offset_seconds\":0,\"marca\":\"" + UUID.randomUUID() + "\"}";
        return new Batch(body, com.projeto.mapi.service.ingestion.Hashing.sha256(body), -8.0, -35.0, hs);
    }

    @Test
    void rodadaAoVivoGravaComInstanteDePublicacao_naoRegravaAMesmaRodada_eIsolaFalha() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant init = now.minus(Duration.ofHours(6));
        Instant avail = now.minus(Duration.ofHours(1));
        when(client.runMeta("ecmwf_ifs")).thenReturn(Optional.of(new RunMeta(init, avail)));
        when(client.runMeta("ecmwf_ifs025")).thenReturn(Optional.of(new RunMeta(init, avail)));
        when(client.runMeta("dwd_icon_global_native")).thenThrow(new IllegalStateException("fora do ar"));
        when(client.runMeta("best_match")).thenReturn(Optional.empty());
        when(client.liveRun(anyDouble(), anyDouble(), any())).thenAnswer(inv -> batch(now.minus(Duration.ofHours(2)), 72, 1.5));

        int first = service.collectLive();
        assertThat(first).isPositive();
        Integer points = jdbc.queryForObject("SELECT count(*) FROM flood_points WHERE active", Integer.class);
        // só as 48 h à frente; a hora com chuva ausente (mas com CAPE) fica com precip_mm nulo, nunca zero
        Integer perModel = jdbc.queryForObject("SELECT count(*) FROM open_meteo_model_hour WHERE model = 'ecmwf_ifs'", Integer.class);
        assertThat(perModel).isEqualTo(points * 48);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM open_meteo_model_hour WHERE model = 'ecmwf_ifs' AND precip_mm IS NULL",
                Integer.class)).isEqualTo(points);
        OffsetDateTime issued = jdbc.queryForObject(
                "SELECT DISTINCT issued_before_utc FROM open_meteo_model_hour WHERE model = 'ecmwf_ifs'", OffsetDateTime.class);
        assertThat(issued.toInstant()).isEqualTo(avail);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM open_meteo_model_hour WHERE model = 'dwd_icon_global_native'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM open_meteo_model_hour WHERE model = 'best_match' AND run_init_utc IS NULL",
                Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT status FROM ingestion_run WHERE source = 'OPEN_METEO_FORECAST' ORDER BY started_at DESC LIMIT 1",
                String.class)).isEqualTo("PARTIAL");

        Integer before = jdbc.queryForObject("SELECT count(*) FROM open_meteo_model_hour WHERE model LIKE 'ecmwf%'", Integer.class);
        service.collectLive();                                    // mesma rodada: nada novo para o ECMWF
        assertThat(jdbc.queryForObject("SELECT count(*) FROM open_meteo_model_hour WHERE model LIKE 'ecmwf%'", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void historicoUsaLimiteConservadorDePublicacaoPorAntecedencia() throws Exception {
        Instant h0 = Instant.parse("2025-06-10T01:00:00Z");
        when(client.previousRuns(anyDouble(), anyDouble(), any(), any(), any(), anyList(), anyBoolean())).thenAnswer(inv -> {
            Map<Integer, Batch> m = new LinkedHashMap<>();
            for (int n = 1; n <= 3; n++) m.put(n, batch(h0, 5, n));
            return m;
        });
        var impl = (com.projeto.mapi.service.weather.impl.OpenMeteoModelServiceImpl)
                org.springframework.test.util.AopTestUtils.getTargetObject(service);
        var m = impl.getClass().getDeclaredMethod("backfill", LocalDate.class);
        m.setAccessible(true);
        m.invoke(impl, LocalDate.now().minusDays(2));
        OffsetDateTime issued = jdbc.queryForObject("""
                SELECT issued_before_utc FROM open_meteo_model_hour
                WHERE product = 'previous_day2' AND hour_end_utc = '2025-06-10T01:00:00Z' LIMIT 1
                """, OffsetDateTime.class);
        assertThat(issued.toInstant()).isEqualTo(h0.minus(Duration.ofHours(48)).plus(Duration.ofHours(8)));
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT product) FROM open_meteo_model_hour", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM open_meteo_model_hour WHERE precip_mm IS NULL AND cape IS NULL",
                Integer.class)).isZero();
    }

    @Test
    void colunaModeloNaV30ValeBestMatchSemMudarAChave() {
        assertThat(jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_name = 'open_meteo_forecast_hour' AND column_name = 'model'
                """, String.class)).contains("best_match");
        assertThat(jdbc.queryForList("""
                SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                WHERE i.indrelid = 'open_meteo_forecast_hour'::regclass AND i.indisprimary
                """, String.class)).containsExactlyInAnyOrder("flood_point_id", "hour_end_utc", "product");
    }
}

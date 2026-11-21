package com.projeto.mapi.integration;

import com.projeto.mapi.controller.AlertaController;
import com.projeto.mapi.dto.AlertaPontoDTO;
import com.projeto.mapi.dto.AlertaTransicaoDTO;
import com.projeto.mapi.dto.TerrainResponseDTO;
import com.projeto.mapi.service.alerta.AlertaCycleService;
import com.projeto.mapi.service.alerta.PointBucketClient;
import com.projeto.mapi.service.flood.RainForecastClient;
import com.projeto.mapi.service.terrain.TerrainQueryService;
import com.projeto.mapi.service.weather.OpenMeteoForecastClient;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Alerta por ponto (V34, ADR 0026): ciclo contra o banco real, estado, transições, motivos de SEM_DADO e a rota. */
@TestPropertySource(properties = {"mapi.rain-forecast.enabled=true", "mapi.rain-forecast.open-meteo-enabled=false",
        "mapi.alerta.flood.terrain-review=AL_REVISAO", "mapi.radar.enabled=false"})
class AlertaIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired AlertaCycleService cycle;
    @Autowired AlertaController controller;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RainForecastClient rainClient;
    @MockitoBean PointBucketClient bucketClient;
    @MockitoBean TerrainQueryService terrain;
    @MockitoBean OpenMeteoForecastClient openMeteo;
    @MockitoBean com.projeto.mapi.service.alagamento.FloodForecastClient floodClient;
    @org.springframework.beans.factory.annotation.Autowired com.projeto.mapi.controller.AlagamentoController alagamento;

    final Instant lastBlock = Instant.ofEpochSecond(Instant.now().getEpochSecond() / 600 * 600);
    UUID live;

    @BeforeEach
    void setUp() {
        for (String t : List.of("flood_forecast_point", "alert_transition", "alert_point_cycle", "alert_point_state", "live_qc_drop")) jdbc.update("DELETE FROM " + t);
        jdbc.update("DELETE FROM alert_cycle");
        jdbc.update("DELETE FROM rain_forecast");
        jdbc.update("DELETE FROM station_alias");
        jdbc.update("DELETE FROM station_canonical");
        ObservationFixtures.clear(jdbc, "CEMADEN_PED");
        ObservationFixtures.clear(jdbc, "CEMADEN");
        jdbc.update("UPDATE flood_points SET active = false");
        jdbc.update("DELETE FROM flood_points WHERE slug IN ('AL_OK', 'AL_REVISAO')");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Ponto OK', 'AL_OK', -8.05, -34.95, true)");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Ponto revisão', 'AL_REVISAO', -8.05, -34.951, true)");
        live = ObservationFixtures.run(jdbc, "CEMADEN");
        ObservationFixtures.station(jdbc, "CEMADEN_PED", "AL_EST", -8.06, -34.95);
        ObservationFixtures.station(jdbc, "CEMADEN", "AL_EST", -8.06, -34.95);
        jdbc.update("INSERT INTO station_canonical (canonical_id, name, lat, lon, primary_source, n_aliases, rule_version) "
                + "VALUES ('AL_EST', 'AL_EST', -8.06, -34.95, 'CEMADEN_PED', 2, 't')");
        jdbc.update("INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version) VALUES ('CEMADEN_PED', 'AL_EST', 'AL_EST', 0, 't')");
        jdbc.update("INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version) VALUES ('CEMADEN', 'AL_EST', 'AL_EST', 0, 't')");
        when(terrain.findBySlug(anyString())).thenAnswer(inv -> terrainOk(inv.getArgument(0)));
    }

    static TerrainResponseDTO terrainOk(String slug) {
        List<TerrainResponseDTO.CurvePoint> curve = new ArrayList<>();
        for (int k = 0; k <= 40; k++) curve.add(new TerrainResponseDTO.CurvePoint(k * 5, 2.0 + k * 0.05, 10.0 * k, 10.0, 100.0));
        return new TerrainResponseDTO(slug, "OK", 2L, 2.1, 1.0, 2.0, 0.1, 1000.0, 2.0, 5.0, curve, null, null, "");
    }

    /** Blocos de 10 min ao vivo por 7 h, terminando no passo mais recente. */
    void blocks(double mmLast) {
        for (int k = 41; k >= 0; k--) {
            Instant end = lastBlock.minus(Duration.ofMinutes(10L * k));
            ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "AL_EST", "PT10M", end, k == 0 ? mmLast : 0.0, "ACTIVE");
        }
    }

    RainForecastClient.Outcome forecast(double probForte) {
        List<RainForecastClient.Horizon> hs = new ArrayList<>();
        for (int h : new int[]{1, 3, 6}) {
            hs.add(new RainForecastClient.Horizon(h, lastBlock.plus(Duration.ofHours(h)), 4.0 * h, 9.0 * h, 20.0 * h, 0.4,
                    probForte, probForte > 0.1055, "gb_rede"));
        }
        return new RainForecastClient.Outcome(new RainForecastClient.Result("rain_v1", lastBlock, hs, List.of("CEMADEN"), List.of(),
                lastBlock, 3.0), false, false, 0.1055, 1);
    }

    static PointBucketClient.Result bucket(double hNow, Integer min50) {
        List<Double> h = IntStream.range(0, 6).mapToObj(i -> 10.0 * i).toList();
        return new PointBucketClient.Result("balde-M1a-10min", hNow, new PointBucketClient.Projection(h, min50, 160.0),
                new PointBucketClient.Projection(h, 20, 220.0), 125.4, 0.4);
    }

    static TestingAuthenticationToken as(String authority) {
        TestingAuthenticationToken t = new TestingAuthenticationToken(authority.toLowerCase(), null, authority);
        t.setAuthenticated(true);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(t);
        return t;
    }

    static TestingAuthenticationToken admin() {
        return as("ADMIN");
    }

    @org.junit.jupiter.api.AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void cicloLigaAtencaoGravaTransicaoEAlagamentoProvavelEDerbyFicaSemDado() {
        blocks(4.0);
        when(rainClient.forecastOutcome(anyDouble(), anyDouble(), anyList(), any())).thenReturn(forecast(0.30));
        when(bucketClient.evaluate(anyList(), any(), eq(5.0), eq(10.0), any(), any())).thenReturn(bucket(40.0, 30));

        AlertaCycleService.CycleSummary s = cycle.run("teste");
        assertThat(s.points()).isEqualTo(2);

        List<AlertaPontoDTO> st = controller.current(admin()).getBody();
        AlertaPontoDTO ok = st.stream().filter(x -> x.slug().equals("AL_OK")).findFirst().orElseThrow();
        assertThat(ok.rainAlert()).isEqualTo("ATENCAO");
        assertThat(ok.pointFlood()).isEqualTo("PROVAVEL");
        assertThat(ok.floodInMinutes()).isEqualTo(30);
        assertThat(ok.rainTeMm()).isEqualTo(4.0);
        assertThat(ok.capacityTeMm()).isCloseTo(20.90, org.assertj.core.data.Offset.offset(0.01));
        assertThat(ok.hNowCm()).isEqualTo(4.0);
        assertThat(ok.shadow()).isTrue();
        assertThat(ok.physicalModelLabel()).contains("não validada");
        assertThat(ok.bucketVersion()).isEqualTo("balde-M1a-10min");
        assertThat(ok.eventId()).isNotNull();
        AlertaPontoDTO rev = st.stream().filter(x -> x.slug().equals("AL_REVISAO")).findFirst().orElseThrow();
        assertThat(rev.pointFlood()).isEqualTo("SEM_DADO");
        assertThat(rev.floodReason()).isEqualTo("TERRENO_EM_REVISAO");
        assertThat(rev.rainAlert()).isEqualTo("ATENCAO");                   // o alerta de chuva continua valendo

        List<AlertaTransicaoDTO> tr = controller.transitions(admin(), "AL_OK", 48, 100).getBody();
        assertThat(tr).extracting(AlertaTransicaoDTO::kind).contains("LIGA");
        assertThat(tr).extracting(AlertaTransicaoDTO::eventId).containsOnly(ok.eventId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_point_cycle", Integer.class)).isEqualTo(2);
    }

    @Test
    void semTerrenoEIaForaViramSemDadoComMotivo() {
        blocks(0.0);
        when(terrain.findBySlug("AL_OK")).thenReturn(new TerrainResponseDTO("AL_OK", "NO_DATA", null, null, null, null, null,
                null, null, null, List.of(), null, null, ""));
        when(rainClient.forecastOutcome(anyDouble(), anyDouble(), anyList(), any()))
                .thenReturn(new RainForecastClient.Outcome(null, false, true, null, null));
        cycle.run("teste");
        AlertaPontoDTO ok = controller.current(admin()).getBody().stream().filter(x -> x.slug().equals("AL_OK")).findFirst().orElseThrow();
        assertThat(ok.rainAlert()).isEqualTo("SEM_DADO");
        assertThat(ok.rainReason()).isEqualTo("IA_INDISPONIVEL");
        assertThat(ok.rainReasonText()).isNotBlank();
        assertThat(ok.pointFlood()).isEqualTo("SEM_DADO");
        assertThat(ok.floodReason()).isEqualTo("SEM_TERRENO");

        // alerta por regressão (V38): o ciclo manda o estado do terreno de cada ponto; IA sem resposta = SEM_DADO com motivo
        org.mockito.Mockito.verify(floodClient).forecast(anyDouble(), anyDouble(), anyList(), anyList(), anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("SEM_TERRENO"), anyDouble(), anyDouble());
        org.mockito.Mockito.verify(floodClient).forecast(anyDouble(), anyDouble(), anyList(), anyList(), anyList(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("EM_REVISAO"), anyDouble(), anyDouble());
        List<com.projeto.mapi.dto.AlagamentoPontoDTO> fl = alagamento.current(admin()).getBody();
        assertThat(fl).extracting(com.projeto.mapi.dto.AlagamentoPontoDTO::slug).contains("AL_OK", "AL_REVISAO");
        assertThat(fl).allSatisfy(f -> {
            assertThat(f.level()).isEqualTo("SEM_DADO");
            assertThat(f.reason()).isEqualTo("PREVISAO_INDISPONIVEL");
            assertThat(f.shadow()).isTrue();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flood_forecast_point", Long.class)).isEqualTo(2L);
    }

    @Test
    void previsaoRecusadaCom422ViraSemDadoProprio() {
        blocks(0.0);
        when(rainClient.forecastOutcome(anyDouble(), anyDouble(), anyList(), any()))
                .thenReturn(new RainForecastClient.Outcome(null, true, false, null, null));
        when(bucketClient.evaluate(anyList(), any(), anyDouble(), anyDouble(), any(), any())).thenReturn(bucket(0.0, null));
        cycle.run("teste");
        AlertaPontoDTO ok = controller.current(admin()).getBody().stream().filter(x -> x.slug().equals("AL_OK")).findFirst().orElseThrow();
        assertThat(ok.rainReason()).isEqualTo("PREVISAO_RECUSADA");
        assertThat(ok.pointFlood()).isEqualTo("SEM_DADO");
    }

    @Test
    void leituraVelhaViraSemDadoESemEstacaoTambem() {
        // último bloco há 30 min: frescor de 15 min estourado
        for (int k = 41; k >= 3; k--) {
            ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "AL_EST", "PT10M", lastBlock.minus(Duration.ofMinutes(10L * k)), 0.0, "ACTIVE");
        }
        when(rainClient.forecastOutcome(anyDouble(), anyDouble(), anyList(), any())).thenReturn(forecast(0.01));
        cycle.run("teste");
        AlertaPontoDTO ok = controller.current(admin()).getBody().stream().filter(x -> x.slug().equals("AL_OK")).findFirst().orElseThrow();
        assertThat(ok.rainAlert()).isEqualTo("SEM_DADO");
        assertThat(ok.rainReason()).isEqualTo("LEITURA_VELHA");
        assertThat(ok.pointFlood()).isEqualTo("SEM_DADO");

        jdbc.update("DELETE FROM observation WHERE source = 'CEMADEN'");
        cycle.run("teste");
        ok = controller.current(admin()).getBody().stream().filter(x -> x.slug().equals("AL_OK")).findFirst().orElseThrow();
        assertThat(ok.rainReason()).isEqualTo("SEM_ESTACAO_VALIDA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_point_state WHERE rain_alert = 'NENHUM' OR point_flood = 'NAO'",
                Integer.class)).isZero();
    }

    @Test
    void emModoSombraSoOAdministradorVe() {
        assertThatThrownBy(() -> controller.current(as("USER")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(controller.current(admin()).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void latenciaSemHoraComChuvaDeclaraQueOOrcamentoNaoFoiMedido() {
        blocks(0.0);
        when(rainClient.forecastOutcome(anyDouble(), anyDouble(), anyList(), any())).thenReturn(forecast(0.01));
        when(bucketClient.evaluate(anyList(), any(), anyDouble(), anyDouble(), any(), any())).thenReturn(bucket(0.0, null));
        cycle.run("teste");
        // linha completa (com recebimento e SSE) para exercitar a conta de cada trecho
        jdbc.update("UPDATE alert_point_cycle SET reading_ts = evaluated_at - interval '9 minutes', "
                + "reading_received_at = evaluated_at - interval '5 minutes', reading_ingested_at = evaluated_at - interval '5 minutes', "
                + "sse_sent_at = state_written_at");
        admin();
        var lat = controller.latency(7).getBody();
        assertThat(lat.segments()).anySatisfy(s -> {
            assertThat(s.segment()).isEqualTo("estacao_coleta");
            assertThat(s.medianS()).isEqualTo(240.0);
        });
        // estação -> SSE = 9 min (a leitura é de 9 min antes do ciclo): fora do orçamento de 5 min
        assertThat(lat.segments()).anySatisfy(s -> {
            assertThat(s.segment()).isEqualTo("total");
            assertThat(s.medianS()).isBetween(539.0, 545.0);
        });
        assertThat(lat.withinBudgetDry()).isFalse();
        assertThat(lat.withinBudgetWet()).isNull();
        assertThat(lat.note()).contains("Sem hora com chuva");
        assertThat(lat.budgetP90Minutes()).isEqualTo(5);
    }
}

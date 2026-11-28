package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.AlagamentoPontoDTO;
import com.projeto.mapi.service.alagamento.FloodForecastClient;
import com.projeto.mapi.service.alerta.AlertaCycleService;
import com.projeto.mapi.service.alerta.PointBucketClient;
import com.projeto.mapi.service.flood.RainForecastClient;
import com.projeto.mapi.service.terrain.TerrainQueryService;
import com.projeto.mapi.service.weather.OpenMeteoForecastClient;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alerta único por ponto (ADR 0030), com a configuração padrão: o ciclo não chama mais o balde do ADR 0026
 * ({@code point_flood = DESLIGADO}), manda a maré de cada passo da série e das próximas 6 h e os dados de limpeza para o
 * {@code /v1/flood/forecast}, e grava as contribuições (V47).
 */
@TestPropertySource(properties = {"mapi.rain-forecast.enabled=true", "mapi.rain-forecast.open-meteo-enabled=false",
        "mapi.alerta.flood.terrain-review=NENHUM", "mapi.radar.enabled=false",
        "mapi.alerta.refluxo.pontos.AL_UNICO.soleira-mdt-m=0.638", "mapi.alerta.refluxo.pontos.AL_UNICO.diametro-m=0.6"})
class AlertaUnicoIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired AlertaCycleService cycle;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean RainForecastClient rainClient;
    @MockitoBean PointBucketClient bucketClient;
    @MockitoBean TerrainQueryService terrain;
    @MockitoBean OpenMeteoForecastClient openMeteo;
    @MockitoBean FloodForecastClient floodClient;
    @MockitoBean com.projeto.mapi.service.alagamento.AiHealthClient aiHealth;
    @Autowired com.projeto.mapi.controller.AlagamentoController alagamento;

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
        ObservationFixtures.clear(jdbc, "DHN_TABUA_PE02");
        jdbc.update("UPDATE flood_points SET active = false");
        jdbc.update("DELETE FROM flood_points WHERE slug IN ('AL_UNICO', 'AL_SEM_SAIDA')");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Único', 'AL_UNICO', -8.05, -34.95, true)");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Sem saída', 'AL_SEM_SAIDA', -8.05, -34.951, true)");
        live = ObservationFixtures.run(jdbc, "CEMADEN");
        ObservationFixtures.station(jdbc, "CEMADEN_PED", "AL_EST", -8.06, -34.95);
        ObservationFixtures.station(jdbc, "CEMADEN", "AL_EST", -8.06, -34.95);
        jdbc.update("INSERT INTO station_canonical (canonical_id, name, lat, lon, primary_source, n_aliases, rule_version) "
                + "VALUES ('AL_EST', 'AL_EST', -8.06, -34.95, 'CEMADEN_PED', 2, 't')");
        jdbc.update("INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version) VALUES ('CEMADEN_PED', 'AL_EST', 'AL_EST', 0, 't')");
        jdbc.update("INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version) VALUES ('CEMADEN', 'AL_EST', 'AL_EST', 0, 't')");
        for (int k = 41; k >= 0; k--) {
            ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "AL_EST", "PT10M", lastBlock.minus(Duration.ofMinutes(10L * k)), 0.0, "ACTIVE");
        }
        // preamares e baixa-mares a cada ~6 h cobrindo a série e as próximas 6 h
        UUID tideRun = ObservationFixtures.run(jdbc, "DHN_TABUA_PE02");
        for (int k = -4; k <= 4; k++) {
            ObservationFixtures.tideExtreme(jdbc, tideRun, lastBlock.plus(Duration.ofMinutes(372L * k)), k % 2 == 0 ? 2.2 : 0.3);
        }
        when(terrain.findBySlug(anyString())).thenAnswer(inv -> AlertaIntegrationTest.terrainOk(inv.getArgument(0)));
        when(rainClient.forecastOutcome(anyDouble(), anyDouble(), anyList(), any())).thenReturn(rain());
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    RainForecastClient.Outcome rain() {
        List<RainForecastClient.Horizon> hs = new java.util.ArrayList<>();
        for (int h : new int[]{1, 3, 6}) {
            hs.add(new RainForecastClient.Horizon(h, lastBlock.plus(Duration.ofHours(h)), 1.0, 2.0, 3.0, 0.1, 0.01, false, "gb_rede"));
        }
        return new RainForecastClient.Outcome(new RainForecastClient.Result("rain_v1", lastBlock, hs, List.of("CEMADEN"), List.of(),
                lastBlock, 3.0), false, false, 0.1055, 1);
    }

    @Test
    void cicloPadraoMandaOsFatoresEGravaAsContribuicoes() throws Exception {
        String ai = """
                {"level": "ATENCAO", "worst_horizon_hours": 3, "h_now_cm": 0.0, "code_version": "abc", "issued_at": "%s",
                 "model_versions": {"flood": "flood_reg_v1"}, "horizons": [],
                 "explanation": {"served_scenario": "q95", "served_horizon_hours": 3, "decomposition": [],
                   "factors": {"refluxo": {"mode": "NIVEL", "status": "OK", "applied_to_level": true, "served_delta_cm": 7.5,
                       "text": "a maré reduz a saída da galeria em 62%% entre 14h00 e 15h30",
                       "periods": [{"from": "%s", "to": "%s", "mean_reduction_pct": 62.0, "max_reduction_pct": 100.0}],
                       "delta_sheet_cm": {"q50": {"1": 0.0, "3": 2.0, "6": 3.0}, "q95": {"1": 0.0, "3": 7.5, "6": 8.0}},
                       "bias_scenario": {"mdt_bias_m": -0.369, "level": "SEM_ALERTA", "worst_horizon_hours": 6, "sheet_cm": {}}},
                     "limpeza": {"mode": "INFO", "status": "SEM_DADO", "applied_to_level": false, "reason": "SEM_TAXA_OU_DATA",
                       "text": "sem dado de limpeza para este ponto"}}}}
                """.formatted(lastBlock, lastBlock.plus(Duration.ofHours(2)), lastBlock.plus(Duration.ofMinutes(210)));
        when(floodClient.forecast(anyDouble(), anyDouble(), anyList(), any(), anyList(), any(), anyString(), anyDouble(),
                anyDouble(), any())).thenReturn(mapper.readTree(ai));

        cycle.run("teste");

        // o balde do ADR 0026 não é mais chamado e point_flood fica DESLIGADO, sem motivo de SEM_DADO
        verify(bucketClient, never()).evaluate(anyList(), any(), anyDouble(), anyDouble(), any(), any());
        verify(bucketClient, never()).evaluate(anyList(), any(), anyDouble(), anyDouble(), any(), any(), any());
        assertThat(jdbc.queryForList("SELECT DISTINCT point_flood FROM alert_point_state", String.class)).containsOnly("DESLIGADO");
        assertThat(jdbc.queryForList("SELECT DISTINCT point_flood FROM alert_point_cycle", String.class)).containsOnly("DESLIGADO");
        // a latência passa a medir o alerta mostrado: instante da IA = resposta do /v1/flood/forecast
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_point_cycle WHERE ai_responded_at IS NOT NULL AND sse_sent_at IS NOT NULL",
                Integer.class)).isEqualTo(2);

        ArgumentCaptor<FloodForecastClient.Factors> fx = ArgumentCaptor.forClass(FloodForecastClient.Factors.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Double>> serie = ArgumentCaptor.forClass(List.class);
        verify(floodClient, org.mockito.Mockito.times(2)).forecast(anyDouble(), anyDouble(), anyList(), any(), serie.capture(),
                any(), eq("OK"), anyDouble(), anyDouble(), fx.capture());
        FloodForecastClient.Factors comSaida = fx.getAllValues().stream().filter(f -> f.sillMdtM() != null).findFirst().orElseThrow();
        FloodForecastClient.Factors semSaida = fx.getAllValues().stream().filter(f -> f.sillMdtM() == null).findFirst().orElseThrow();
        int n = serie.getAllValues().get(0).size();
        assertThat(comSaida.refluxoMode()).isEqualTo("NIVEL");
        assertThat(comSaida.tideNrM()).hasSize(n + 36).doesNotContainNull();
        assertThat(comSaida.tideNrM()).allSatisfy(v -> assertThat(v).isBetween(0.3, 2.2));
        assertThat(comSaida.sillMdtM()).isEqualTo(0.638);
        assertThat(comSaida.deltaM()).isEqualTo(0.821);
        assertThat(comSaida.mdtBiasM()).isZero();
        assertThat(comSaida.mdtBiasScenarioM()).isEqualTo(-0.369);
        assertThat(comSaida.limpezaMode()).isEqualTo("INFO");
        // ponto sem saída cadastrada: vai o modo sem maré (a IA devolve NAO_SE_APLICA), nunca maré inventada
        assertThat(semSaida.tideNrM()).isNull();

        assertThat(jdbc.queryForList("SELECT refluxo_mode || '|' || limpeza_mode || '|' || refluxo_delta_cm || '|' || bias_scenario_level "
                + "FROM flood_forecast_point", String.class)).containsOnly("NIVEL|INFO|7.5|SEM_ALERTA");

        AlertaIntegrationTest.admin();
        AlagamentoPontoDTO dto = alagamento.current(AlertaIntegrationTest.admin()).getBody().get(0);
        AlagamentoPontoDTO.Refluxo r = dto.explanation().factors().refluxo();
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.periods()).singleElement().satisfies(p -> assertThat(p.meanReductionPct()).isEqualTo(62.0));
        assertThat(r.deltaSheetCm().get("q95")).containsEntry("3", 7.5);
        assertThat(r.biasScenario().level()).isEqualTo("SEM_ALERTA");
        assertThat(dto.explanation().factors().limpeza().reason()).isEqualTo("SEM_TAXA_OU_DATA");
    }
}

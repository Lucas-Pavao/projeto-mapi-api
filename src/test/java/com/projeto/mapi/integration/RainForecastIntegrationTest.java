package com.projeto.mapi.integration;

import com.projeto.mapi.dto.RainForecastAccuracyDTO;
import com.projeto.mapi.dto.RainForecastDTO;
import com.projeto.mapi.repository.analise.RainForecastRepository.OmHour;
import com.projeto.mapi.repository.analise.RainForecastRepository.Reading;
import com.projeto.mapi.service.flood.RainForecastClient;
import com.projeto.mapi.service.flood.RainForecastService;
import com.projeto.mapi.service.weather.OpenMeteoForecastClient;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Previsão de chuva (V30): vizinhança de até 40 km, só o emitido até t, gravação e conferência contra o medido. */
@TestPropertySource(properties = "mapi.rain-forecast.enabled=true")
class RainForecastIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired RainForecastService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RainForecastClient client;
    @MockitoBean OpenMeteoForecastClient openMeteo;

    long pointId;
    UUID ped, live;
    final Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);
    final Instant issued = now.minus(Duration.ofHours(5));

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM rain_forecast");
        jdbc.update("DELETE FROM open_meteo_forecast_hour");
        jdbc.update("DELETE FROM station_alias");           // outros testes deixam apelidos que prendem as estações
        jdbc.update("DELETE FROM station_canonical");
        ObservationFixtures.clear(jdbc, "CEMADEN_PED");
        ObservationFixtures.clear(jdbc, "CEMADEN");
        jdbc.update("DELETE FROM flood_points WHERE slug = 'RF_PONTO'");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, active) VALUES ('Ponto da chuva', 'RF_PONTO', -8.05, -34.95, true)");
        pointId = jdbc.queryForObject("SELECT id FROM flood_points WHERE slug = 'RF_PONTO'", Long.class);
        ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        live = ObservationFixtures.run(jdbc, "CEMADEN");
        canonical("RF_PERTO", -8.06, -34.95);      // ~1 km
        canonical("RF_PERTO2", -8.05, -34.93);     // ~2 km
        canonical("RF_OESTE", -8.05, -35.10);      // ~16 km, a oeste
        canonical("RF_LONGE", -8.05, -35.60);      // ~70 km: fora
    }

    private void canonical(String id, double lat, double lon) {
        ObservationFixtures.station(jdbc, "CEMADEN_PED", id, lat, lon);
        ObservationFixtures.station(jdbc, "CEMADEN", id, lat, lon);
        jdbc.update("INSERT INTO station_canonical (canonical_id, name, lat, lon, primary_source, n_aliases, rule_version) "
                + "VALUES (?, ?, ?, ?, 'CEMADEN_PED', 2, 't')", id, id, lat, lon);
        jdbc.update("INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version) VALUES ('CEMADEN_PED', ?, ?, 0, 't')", id, id);
        jdbc.update("INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version) VALUES ('CEMADEN', ?, ?, 0, 't')", id, id);
    }

    private void block(String source, String station, Instant end, double mm) {
        ObservationFixtures.precipitation(jdbc, "CEMADEN_PED".equals(source) ? ped : live, source, station, "PT10M", end, mm, "ACTIVE");
    }

    private RainForecastClient.Result result(Instant t) {
        List<RainForecastClient.Horizon> hs = new ArrayList<>();
        for (int h : new int[]{1, 3, 6}) {
            hs.add(new RainForecastClient.Horizon(h, t.plus(Duration.ofHours(h)), 1.0 * h, 2.0 * h, 6.0 * h, 0.2, 0.01, false, "gb_rede"));
        }
        return new RainForecastClient.Result("rain_v1", t, hs, List.of("CEMADEN_PED"), List.of("om_6h"), t, 12.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emite_comVizinhancaDe40km_umaFontePorHora_eSoOpenMeteoEmitidoAteT() {
        Instant t = now.minus(Duration.ofMinutes(20));
        block("CEMADEN_PED", "RF_PERTO", t.minus(Duration.ofMinutes(70)), 1.5);      // hora anterior: a PED vale
        block("CEMADEN", "RF_PERTO", t.minus(Duration.ofMinutes(70)), 9.9);          // mesma hora ao vivo: descartada
        block("CEMADEN", "RF_PERTO", t, 0.4);                                         // hora corrente: só ao vivo, vale
        block("CEMADEN_PED", "RF_OESTE", t.minus(Duration.ofMinutes(10)), 2.0);
        block("CEMADEN_PED", "RF_LONGE", t.minus(Duration.ofMinutes(10)), 50.0);     // 70 km: fora
        Instant h0 = t.truncatedTo(ChronoUnit.HOURS);
        List<OpenMeteoForecastClient.Hour> om = new ArrayList<>();
        for (int i = -24; i <= 30; i++) om.add(new OpenMeteoForecastClient.Hour(h0.plus(Duration.ofHours(i)), i > 28 ? null : 0.3));
        when(openMeteo.previousDayRun(anyDouble(), anyDouble())).thenReturn(new OpenMeteoForecastClient.Batch(-8.05, -34.92, "c".repeat(64), om));
        when(client.forecast(anyDouble(), anyDouble(), any(), any())).thenReturn(Optional.of(result(t)));

        Optional<RainForecastDTO> out = service.forecastAndStore(pointId, "RF_PONTO", -8.05, -34.95);

        ArgumentCaptor<List<Reading>> readings = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<OmHour>> omSent = ArgumentCaptor.forClass(List.class);
        verify(client).forecast(anyDouble(), anyDouble(), readings.capture(), omSent.capture());
        assertThat(readings.getValue()).extracting(Reading::stationId).containsExactlyInAnyOrder("RF_PERTO", "RF_PERTO", "RF_OESTE");
        assertThat(readings.getValue()).extracting(Reading::mm).containsExactlyInAnyOrder(1.5, 0.4, 2.0);
        // Open-Meteo: horas de (h0, h0+7h], todas emitidas (valid - 24 h) antes de t; hora sem valor não é gravada
        assertThat(omSent.getValue()).hasSize(7).allMatch(o -> o.hourEndUtc().isAfter(h0) && o.mm() == 0.3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM open_meteo_forecast_hour WHERE mm IS NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT bool_and(issued_before_utc = hour_end_utc - interval '24 hours') FROM open_meteo_forecast_hour", Boolean.class)).isTrue();

        assertThat(out).isPresent();
        assertThat(out.get().horizons()).extracting(RainForecastDTO.Horizon::horizonHours).containsExactly(1, 3, 6);
        assertThat(out.get().attribution()).contains("CEMADEN/MCTIC");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rain_forecast WHERE flood_point_id = ?", Integer.class, pointId)).isEqualTo(3);
        assertThat(service.latest()).filteredOn(f -> f.slug().equals("RF_PONTO")).singleElement()
                .satisfies(f -> assertThat(f.missingInputs()).containsExactly("om_6h"));
    }

    @Test
    void semLeituraRecente_naoEmite_eNaoChamaAIA() {
        block("CEMADEN_PED", "RF_PERTO", now.minus(Duration.ofHours(9)), 3.0);
        assertThat(service.forecastAndStore(pointId, "RF_PONTO", -8.05, -34.95)).isEmpty();
        verify(client, org.mockito.Mockito.never()).forecast(anyDouble(), anyDouble(), any(), any());
    }

    @Test
    void conferencia_usaOMaiorAcumuladoDasEstacoesValidasPerto_eSemMedidaNaoViraZero() {
        when(openMeteo.previousDayRun(anyDouble(), anyDouble())).thenThrow(new IllegalStateException("fora"));
        // leitura recente só para a emissão acontecer; a IA devolve uma previsão emitida há 5 h
        block("CEMADEN_PED", "RF_PERTO", now.minus(Duration.ofMinutes(30)), 0.0);
        when(client.forecast(anyDouble(), anyDouble(), any(), any())).thenReturn(Optional.of(result(issued)));
        assertThat(service.forecastAndStore(pointId, "RF_PONTO", -8.05, -34.95)).isPresent();

        // medido: RF_PERTO válida no início e no fim da janela de 1 h, com 4 + 30 mm; RF_PERTO2 sem leitura no início (fora)
        block("CEMADEN_PED", "RF_PERTO", issued, 0.0);
        block("CEMADEN_PED", "RF_PERTO", issued.plus(Duration.ofMinutes(20)), 4.0);
        block("CEMADEN_PED", "RF_PERTO", issued.plus(Duration.ofMinutes(60)), 30.0);
        block("CEMADEN_PED", "RF_PERTO2", issued.plus(Duration.ofMinutes(50)), 80.0);
        block("CEMADEN_PED", "RF_OESTE", issued.plus(Duration.ofMinutes(30)), 99.0);    // 16 km: não é a chuva do ponto

        int n = service.verifyPending();
        // 1 h e 3 h venceram há mais de 2 h; a de 6 h (vence daqui a 1 h) continua pendente
        assertThat(n).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT observed_mm FROM rain_forecast WHERE horizon_hours = 1", Double.class)).isCloseTo(34.0, within(1e-9));
        assertThat(jdbc.queryForObject("SELECT observed_peak_mm_h FROM rain_forecast WHERE horizon_hours = 1", Double.class)).isCloseTo(34.0, within(1e-9));
        // 3 h: nenhuma estação próxima tem leitura nos 70 min antes do fim da janela -> sem medida, não zero
        assertThat(jdbc.queryForObject("SELECT verification_note FROM rain_forecast WHERE horizon_hours = 3", String.class)).isEqualTo("NO_MEASURE");
        assertThat(jdbc.queryForObject("SELECT observed_mm FROM rain_forecast WHERE horizon_hours = 3", Double.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT verified_at FROM rain_forecast WHERE horizon_hours = 6", java.sql.Timestamp.class)).isNull();

        RainForecastAccuracyDTO acc = service.accuracy(30);
        assertThat(acc.pending()).isEqualTo(1);
        RainForecastAccuracyDTO.Row h1 = acc.horizons().stream().filter(r -> r.horizonHours() == 1).findFirst().orElseThrow();
        // medido 34; quantis 1, 2 e 6: pinball = (0,5*33 + 0,8*32 + 0,95*28) / 3
        assertThat(h1.pinballMm()).isCloseTo((0.5 * 33 + 0.8 * 32 + 0.95 * 28) / 3, within(1e-9));
        assertThat(h1.coverageQ95()).isZero();
        assertThat(h1.forteEvents()).isEqualTo(1);
        assertThat(acc.horizons().stream().filter(r -> r.horizonHours() == 3).findFirst().orElseThrow().withoutMeasure()).isEqualTo(1);
    }
}

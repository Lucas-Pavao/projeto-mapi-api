package com.projeto.mapi.service.ingestion.adapter.ped;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.ingestion.PedFetchWindowRepository;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.StationInfo;
import com.projeto.mapi.service.ingestion.validation.SourceTzRule;
import com.projeto.mapi.service.ingestion.validation.ValidationService;
import com.projeto.mapi.service.ingestion.validation.VariableRule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adaptador da PED (ADR 0021): duração do bloco independente da janela, bordas, cabeçalho e "sem dado". */
class CemadenPedAdapterTest {

    private static final String HEAD = "OBS.: PCD com horario UTC!\n" + CemadenPedAdapter.COLUMNS + "\n";
    private static final Instant START = Instant.parse("2022-05-01T00:00:00Z");
    private static final Instant END = Instant.parse("2022-05-01T06:00:00Z");

    private static String line(String ts, String sensor, String value, String q) {
        return "261160609A;Imbiribeira;RECIFE;PE;-8.120975;-34.913983;" + ts + ";" + sensor + ";" + value + ";" + q + ";\n";
    }

    private final CemadenPedClient client = mock(CemadenPedClient.class);
    private final PedFetchWindowRepository windows = mock(PedFetchWindowRepository.class);
    private final FloodPointRepository points = mock(FloodPointRepository.class);
    private final IngestionProperties props = new IngestionProperties();

    private CemadenPedAdapter adapter() {
        return new CemadenPedAdapter(client, windows, points, props, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void blocoUsaLeituraAnteriorDoLookbackEDescartaBordas() {
        String body = HEAD
                + line("2022-04-30 23:20:00", "chuva", "0.0", "0000")   // lookback: só contexto
                + line("2022-04-30 23:20:00", "intensidade_precipitacao", "0.0", "0000")
                + line("2022-05-01 00:10:00", "chuva", "1.4", "0004")   // anterior a 50 min: bloco de 50 min
                + line("2022-05-01 00:20:00", "chuva", "2.0", "0004")   // bloco de 10 min
                + line("2022-05-01 04:00:00", "chuva", "0.0", "0000")   // anterior a 220 min: fora do lookback
                + line("2022-05-01 06:00:00", "chuva", "9.9", "0004");  // fim exclusivo: é da próxima janela
        List<RawRecord> recs = adapter().parse(body, "261160609A", START, END, Instant.now());

        assertThat(recs).extracting(RawRecord::sourceTsText)
                .containsExactly("2022-05-01 00:10:00", "2022-05-01 00:20:00", "2022-05-01 04:00:00");
        List<DraftValue> d0 = adapter().extract(recs.get(0));
        assertThat(d0).singleElement().satisfies(d -> {
            assertThat(d.value()).isEqualTo(1.4);
            assertThat(d.resolution()).isEqualTo("EVENT");
            assertThat(d.qualifier()).isEqualTo("0004");
            assertThat(d.window()).isEqualTo(Duration.ofMinutes(50));
        });
        assertThat(adapter().extract(recs.get(1)).get(0).window()).isEqualTo(Duration.ofMinutes(10));
        // sem anterior no lookback (70 min): bloco declarado com 71 min, acima do máximo
        assertThat(adapter().extract(recs.get(2)).get(0).window()).isEqualTo(Duration.ofMinutes(71));
        assertThat(recs.get(0).sourceTz()).isEqualTo("UTC");
        assertThat(recs.get(0).payload().path("linha").asText()).contains("chuva;1.4;0004");
    }

    @Test
    void mesmaLeituraTemOMesmoBlocoQualquerQueSejaAJanela() {
        String body = HEAD + line("2022-05-01 05:40:00", "chuva", "0.0", "0000")
                + line("2022-05-01 06:10:00", "chuva", "3.0", "0004");
        // a leitura de 06:10 pertence à janela que começa às 06:00; a anterior (05:40) está no lookback dela
        List<RawRecord> next = adapter().parse(body, "261160609A", END, END.plus(Duration.ofHours(6)), Instant.now());
        assertThat(next).hasSize(1);
        assertThat(adapter().extract(next.get(0)).get(0).window()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void cabecalhoSemUtcFalhaDeFormaVisivel() {
        String body = "cod.estacao;nome\n" + line("2022-05-01 00:10:00", "chuva", "1.0", "0004");
        assertThatThrownBy(() -> adapter().parse(body, "261160609A", START, END, Instant.now()))
                .isInstanceOf(SourceUnavailableException.class).hasMessageContaining("cabeçalho");
    }

    @Test
    void semResultadoViraLoteVazioComJanelaMarcadaNoData() {
        StationInfo st = new StationInfo("CEMADEN_PED", "261160609A", "Imbiribeira", -8.12, -34.91, "RECIFE",
                new ObjectMapper().createObjectNode());
        FetchedBatch b = adapter().toBatch(new CemadenPedClient.Response(202, "u",
                "Info\nNenhum resultado foi encontrado para essa requisição!", true), st, START, END, List.of());
        assertThat(b.records()).isEmpty();
        b.inTransaction().run();
        verify(windows).markDone(eq("261160609A"), eq(START), eq(END), eq("NO_DATA"), eq(202), eq(0), anyString(),
                eq("u"), eq("cemaden-ped/1"), any());
    }

    @Test
    void cargaPulaJanelaJaGravadaEAntesDaInstalacao() {
        FloodPoint p = new FloodPoint();
        p.setLatitude(-8.118123);
        p.setLongitude(-34.904945);
        when(points.findByActiveTrue()).thenReturn(List.of(p));
        props.getCemadenPed().setIbgeCodes(List.of("2611606"));
        props.getCemadenPed().setWindowDays(1);
        when(client.registry("2611606")).thenReturn(new CemadenPedClient.Response(200, "r", """
                [{"codestacao":"261160609A","nome":"Imbiribeira","cidade":"RECIFE","latitude":-8.121,"longitude":-34.914,
                  "data_instalacao":"2022-05-02 10:00:00.000","id_tipoestacao":1},
                 {"codestacao":"999","nome":"Longe","cidade":"X","latitude":-7.0,"longitude":-35.0}]
                """, false));
        Instant d3 = START.plus(Duration.ofDays(3));
        when(windows.doneKeys()).thenReturn(Set.of(PedFetchWindowRepository.key("261160609A",
                START.plus(Duration.ofDays(1)), START.plus(Duration.ofDays(2)))));
        when(client.rain(anyString(), any(), any())).thenReturn(new CemadenPedClient.Response(202, "u", "", true));
        List<FetchedBatch> got = new ArrayList<>();
        adapter().fetch(START, d3, got::add);
        // dia 1: antes da instalação; dia 2: já gravado; só o dia 3 é pedido, com lookback de 70 min
        verify(client, times(1)).rain(eq("261160609A"), eq(START.plus(Duration.ofDays(2)).minus(Duration.ofMinutes(70))),
                eq(d3));
        verify(client, never()).rain(eq("999"), any(), any());
        assertThat(got).hasSize(1);
        assertThat(got.get(0).stations()).extracting(StationInfo::stationId).containsExactly("261160609A");
    }

    @Test
    void validacaoMandaBlocoPosLacunaParaQuarentenaEGuardaInicioDoBloco() {
        ValidationService v = new ValidationService(List.of(-9999.0));
        Map<String, VariableRule> cat = Map.of("precipitation",
                new VariableRule("precipitation", "mm", 0.0, 300.0, "[sem fonte]", "PT1H", "END", "SUM", 0.8));
        String body = HEAD + line("2022-05-01 00:10:00", "chuva", "1.4", "0004")
                + line("2022-05-01 00:20:00", "chuva", "2.0", "0004");
        List<RawRecord> recs = adapter().parse(body, "261160609A", START, END, Instant.now());
        SourceTzRule utc = new SourceTzRule("UTC", "VERIFIED");

        var first = v.validate(recs.get(0), utc, adapter().extract(recs.get(0)), cat);
        assertThat(first.valid()).isEmpty();
        assertThat(first.rejections()).singleElement().satisfies(r -> {
            assertThat(r.ruleId()).isEqualTo("GAP");
            assertThat(r.reason()).isEqualTo("AFTER_GAP");
        });
        var second = v.validate(recs.get(1), utc, adapter().extract(recs.get(1)), cat);
        assertThat(second.valid()).singleElement().satisfies(o -> {
            assertThat(o.tsUtc()).isEqualTo(Instant.parse("2022-05-01T00:20:00Z"));
            assertThat(o.intervalStartUtc()).isEqualTo(Instant.parse("2022-05-01T00:10:00Z"));
            assertThat(o.qualifier()).isEqualTo("0004");
        });
    }

    @Test
    void qualificacaoSuspeitaVaiParaQuarentenaSoNaFonteConfigurada() {
        ValidationService v = new ValidationService(List.of(-9999.0), ValidationService.DEFAULT_MAX_BLOCK,
                Map.of("CEMADEN_PED", Set.of("0002")));
        Map<String, VariableRule> cat = Map.of("precipitation",
                new VariableRule("precipitation", "mm", 0.0, 300.0, "[sem fonte]", "PT1H", "END", "SUM", 0.8));
        String body = HEAD + line("2022-05-01 00:10:00", "chuva", "0.0", "0000")
                + line("2022-05-01 00:20:00", "chuva", "215.6744", "0002")
                + line("2022-05-01 00:30:00", "chuva", "2.0", "0004");
        List<RawRecord> recs = adapter().parse(body, "261160609A", START, END, Instant.now());
        SourceTzRule utc = new SourceTzRule("UTC", "VERIFIED");
        var suspect = v.validate(recs.get(1), utc, adapter().extract(recs.get(1)), cat);
        assertThat(suspect.valid()).isEmpty();
        assertThat(suspect.rejections()).singleElement().satisfies(r -> {
            assertThat(r.ruleId()).isEqualTo("QUALIFIER");
            assertThat(r.reason()).isEqualTo("SUSPECT_QUALIFIER");
        });
        assertThat(v.validate(recs.get(2), utc, adapter().extract(recs.get(2)), cat).valid()).hasSize(1);
    }
}

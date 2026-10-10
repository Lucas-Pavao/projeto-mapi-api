package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.TabuaMareResponse;
import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.tide.DhnTabuaCsvAdapter;
import com.projeto.mapi.service.ingestion.adapter.tide.TabuaMareApiAdapter;
import com.projeto.mapi.service.ingestion.adapter.tide.TideHourlyAdapter;
import com.projeto.mapi.service.tide.TabuaMareService;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Maré prevista (Porto do Recife, pe02) ponta a ponta: tábua DHN em CSV, API TabuaMare simulada e série horária. */
class TideIngestionIntegrationTest extends AbstractIngestionTest {

    @MockitoBean private TabuaMareService tabuaMare;
    @Autowired private ObjectMapper mapper;

    private static final Instant JAN1 = Instant.parse("2023-01-01T03:00:00Z");   // 2023-01-01 00:00 em UTC-3
    private static final Instant JAN2 = Instant.parse("2023-01-02T03:00:00Z");
    private static final Instant JAN3 = Instant.parse("2023-01-03T03:00:00Z");

    private IngestionRunView run(String source, Instant from, Instant to) {
        return pipeline.execute(new IngestionRequest(source, from, to, IngestionMode.REFETCH, IngestionKind.BACKFILL));
    }

    // -------------------------------------------------------------------- tábua DHN (CSV)

    /** Fuso: 00:00 local (UTC-3) da tábua vira 03:00Z; o bruto guarda o texto original e o fuso declarado. */
    @Test
    void conversaoDeFusoEExtremosDoPrimeiroDia() {
        IngestionRunView r = run(DhnTabuaCsvAdapter.SOURCE, JAN1, JAN3);
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.obsInserted()).isEqualTo(8); // 4 extremos em 1 e 4 em 2 de janeiro de 2023 (tábua DHN)

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT to_char(ts_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') AS ts, value, qualifier, unit
                FROM observation WHERE source = 'DHN_TABUA_PE02' AND variable = 'tide_height' AND resolution = 'EVENT'
                ORDER BY ts_utc LIMIT 4""");
        assertThat(rows).extracting(m -> m.get("ts").toString())
                .containsExactly("2023-01-01 03:00:00", "2023-01-01 09:11:00", "2023-01-01 15:08:00", "2023-01-01 21:34:00");
        assertThat(rows).extracting(m -> m.get("value")).containsExactly(1.9, 0.7, 1.8, 0.7);
        assertThat(rows).extracting(m -> m.get("qualifier")).containsExactly("PREAMAR", "BAIXA_MAR", "PREAMAR", "BAIXA_MAR");
        assertThat(rows).allSatisfy(m -> assertThat(m.get("unit")).isEqualTo("m"));

        assertThat(jdbc.queryForObject("""
                SELECT source_ts_text || '|' || source_tz || '|' || (payload->>'vertical_reference')
                FROM raw_observation WHERE source = 'DHN_TABUA_PE02' AND source_ts_norm = '2023-01-01T00:00'""", String.class))
                .isEqualTo("2023-01-01 00:00|UTC-03:00|NR_DHN_pe02");
        assertThat(jdbc.queryForObject("SELECT attrs->>'vertical_reference' FROM station WHERE source = 'DHN_TABUA_PE02'",
                String.class)).isEqualTo("NR_DHN_pe02");
    }

    /** T1: repetir a carga não cria bruto, observação nem revisão. */
    @Test
    void idempotencia() {
        run(DhnTabuaCsvAdapter.SOURCE, JAN1, JAN3);
        IngestionRunView again = run(DhnTabuaCsvAdapter.SOURCE, JAN1, JAN3);
        assertThat(again.rawInserted()).isZero();
        assertThat(again.obsInserted()).isZero();
        assertThat(again.obsUpdated()).isZero();
        assertThat(count("SELECT count(*) FROM observation_revision")).isZero();
    }

    /** T1 entre janelas: a classe PREAMAR/BAIXA_MAR do extremo de borda não muda quando a janela cresce. */
    @Test
    void classificacaoNaoDependeDaJanela() {
        run(DhnTabuaCsvAdapter.SOURCE, JAN1, JAN2);
        IngestionRunView wider = run(DhnTabuaCsvAdapter.SOURCE, JAN1, JAN3);
        assertThat(wider.obsUpdated()).isZero();
        assertThat(wider.obsInserted()).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM observation_revision")).isZero();
    }

    @Test
    void anoSemTabuaFalhaEmVezDeRespostaVazia() {
        IngestionRunView r = run(DhnTabuaCsvAdapter.SOURCE, Instant.parse("2030-01-01T00:00:00Z"), Instant.parse("2030-01-02T00:00:00Z"));
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.errorSummary()).contains("Sem tábua DHN");
    }

    // -------------------------------------------------------------------- série horária interpolada

    /** T6 (borda da hora) e interpolação com resposta conhecida; a hora 03:00Z é o extremo exato. */
    @Test
    void serieHorariaBordaDaHoraEInterpolacao() {
        run(DhnTabuaCsvAdapter.SOURCE, JAN1, JAN3);
        IngestionRunView h = run(TideHourlyAdapter.SOURCE, JAN1, Instant.parse("2023-01-02T00:00:00Z"));
        assertThat(h.status()).isEqualTo("OK");
        assertThat(h.obsInserted()).isEqualTo(21); // 03:00Z..23:00Z

        // o extremo das 03:00Z pertence à linha horária 03:00 (não à das 02:00, que nem existe)
        assertThat(jdbc.queryForObject("SELECT value FROM observation_hourly_v WHERE source = 'TIDE_PE02_HOURLY' "
                + "AND hour_end_utc = '2023-01-01T03:00:00Z'", Double.class)).isEqualTo(1.9);
        assertThat(count("SELECT count(*) FROM observation_hourly_v WHERE source = 'TIDE_PE02_HOURLY' "
                + "AND hour_end_utc = '2023-01-01T02:00:00Z'")).isZero();
        // 06:00Z fica a 10800 s de 22260 s entre 1,9 m e 0,7 m: 1,9 - 1,2*(1-cos(pi*0,485175))/2 = 1,32793
        assertThat(jdbc.queryForObject("SELECT value FROM observation_hourly_v WHERE source = 'TIDE_PE02_HOURLY' "
                + "AND hour_end_utc = '2023-01-01T06:00:00Z'", Double.class)).isCloseTo(1.3279, within(1e-4));
        assertThat(jdbc.queryForObject("SELECT unit || '|' || variable FROM observation_hourly_v "
                + "WHERE source = 'TIDE_PE02_HOURLY' LIMIT 1", String.class)).isEqualTo("m|tide_height_hourly");

        IngestionRunView again = run(TideHourlyAdapter.SOURCE, JAN1, Instant.parse("2023-01-02T00:00:00Z"));
        assertThat(again.rawInserted()).isZero();
        assertThat(again.obsInserted()).isZero();
        assertThat(again.obsUpdated()).isZero();
    }

    // -------------------------------------------------------------------- API TabuaMare

    private static final String OCT = """
            {"id":"pe02","year":2026,"harbor_name":"PORTO DO RECIFE (ESTADO DE PERNAMBUCO)","state":"pe","timezone":"%s",
             "card":"902","data_collection_institution":"DHN","mean_level":1.28,
             "months":[{"month_name":"Outubro","month":10,"days":[
              {"weekday_name":"sexta","day":9,"hours":[{"hour":"02:47:00","level":2.4},{"hour":"09:06:00","level":0.2},
                {"hour":"15:04:00","level":2.29},{"hour":"21:17:00","level":0.2}]},
              {"weekday_name":"sábado","day":10,"hours":[{"hour":"03:23:00","level":2.45},{"hour":"09:38:00","level":0.2},
                {"hour":"15:40:00","level":2.34},{"hour":"21:53:00","level":%s}]}]}]}
            """;

    private void respond(String timezone, String lastLevel) throws Exception {
        Object harbor = mapper.readValue(OCT.formatted(timezone, lastLevel), Map.class);
        when(tabuaMare.getTideTable(eq("pe02"), anyInt(), any(String.class))).thenReturn(
                TabuaMareResponse.<List<Object>>builder().data(List.of(harbor)).total(1).build());
    }

    @Test
    void apiConverteUtcMenos3EClassificaExtremos() throws Exception {
        respond("UTC -03.0", "0.15");
        // dia 10 de outubro de 2026 em Recife = [2026-10-10T03:00Z, 2026-10-11T03:00Z)
        IngestionRunView r = run(TabuaMareApiAdapter.SOURCE, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-10-11T03:00:00Z"));
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.obsInserted()).isEqualTo(4);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT to_char(ts_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') AS ts, value, qualifier FROM observation
                WHERE source = 'TABUAMARE_PE02' ORDER BY ts_utc""");
        assertThat(rows).extracting(m -> m.get("ts").toString()).containsExactly(
                "2026-10-10 06:23:00", "2026-10-10 12:38:00", "2026-10-10 18:40:00", "2026-10-11 00:53:00");
        assertThat(rows).extracting(m -> m.get("value")).containsExactly(2.45, 0.2, 2.34, 0.15);
        assertThat(rows).extracting(m -> m.get("qualifier")).containsExactly("PREAMAR", "BAIXA_MAR", "PREAMAR", "BAIXA_MAR");

        IngestionRunView again = run(TabuaMareApiAdapter.SOURCE, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-10-11T03:00:00Z"));
        assertThat(again.obsInserted()).isZero();
        assertThat(again.obsUpdated()).isZero();
        assertThat(again.rawInserted()).isZero();
    }

    /** A API só serve o ano corrente: pedir 2025 e receber "year":2026 deve falhar, não rotular 2026 como 2025. */
    @Test
    void apiNaoRotulaOutroAnoComoOPedido() throws Exception {
        respond("UTC -03.0", "0.15");
        IngestionRunView r = run(TabuaMareApiAdapter.SOURCE, Instant.parse("2025-10-10T03:00:00Z"), Instant.parse("2025-10-11T03:00:00Z"));
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.errorSummary()).contains("não 2025");
        assertThat(count("SELECT count(*) FROM observation WHERE source = 'TABUAMARE_PE02'")).isZero();
    }

    @Test
    void apiComFusoDiferenteDeUtcMenos3Falha() throws Exception {
        respond("UTC -04.0", "0.15");
        IngestionRunView r = run(TabuaMareApiAdapter.SOURCE, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-10-11T03:00:00Z"));
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.errorSummary()).contains("timezone");
    }

    /** Faixa física conservadora (-0,5 a 3,0 m): altura fora vai para a quarentena, sem observação. */
    @Test
    void alturaForaDaFaixaVaiParaQuarentena() throws Exception {
        respond("UTC -03.0", "3.4");
        run(TabuaMareApiAdapter.SOURCE, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-10-11T03:00:00Z"));
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE reason = 'OUT_OF_RANGE'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM observation WHERE source = 'TABUAMARE_PE02'")).isEqualTo(3);
    }
}

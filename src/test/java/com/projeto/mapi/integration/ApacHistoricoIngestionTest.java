package com.projeto.mapi.integration;

import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.ApacHistoricoClient;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * APAC histórica (ADR 0020): planilha real exportada em 2026-11-08 (RMR, maio de 2022, 21 postos convencionais) até
 * {@code observation} como total diário P1D, dia civil de Recife, carimbo no fim do dia.
 */
class ApacHistoricoIngestionTest extends AbstractIngestionTest {

    private static final Instant FROM = Instant.parse("2022-05-01T03:00:00Z");
    private static final Instant TO = Instant.parse("2022-06-01T03:00:00Z");

    private byte[] xlsx() throws IOException {
        try (var in = getClass().getResourceAsStream("/apac/historico-diario-rmr-2022-05.xlsx")) {
            return in.readAllBytes();
        }
    }

    private IngestionRunView run() {
        return pipeline.execute(new IngestionRequest("APAC_HIST_DIARIO", FROM, TO, IngestionMode.REFETCH,
                IngestionKind.BACKFILL));
    }

    @Test
    void totalDiarioViraP1DComFimDoDiaLocal() throws IOException {
        when(apacHistorico.daily(any(), any())).thenReturn(new ApacHistoricoClient.Response("u", "f", xlsx()));
        IngestionRunView r = run();
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.quarantined()).isZero();

        // Codecipe/Santo Amaro, 28/05/2022 = 190,00 mm; o dia local termina em 29/05 00:00 -03 = 03:00Z
        Map<String, Object> o = jdbc.queryForMap("SELECT value, resolution, variable FROM observation "
                + "WHERE source = 'APAC_HIST_DIARIO' AND station_id = '261160602V' AND ts_utc = '2022-05-29T03:00:00Z'");
        assertThat(o.get("value")).isEqualTo(190.0);
        assertThat(o.get("resolution")).isEqualTo("P1D");
        assertThat(o.get("variable")).isEqualTo("precipitation_daily_total");

        // "-" é ausência, não zero: Olinda (Alto da Bondade) tem só 8 dias com valor em maio
        assertThat(count("SELECT count(*) FROM observation WHERE source = 'APAC_HIST_DIARIO' AND station_id = '260960002V'"))
                .isEqualTo(8);
        assertThat(count("SELECT count(*) FROM observation WHERE source = 'APAC_HIST_DIARIO' AND station_id = '261160602V'"))
                .isEqualTo(31);
        // 625 células com valor na planilha
        assertThat(count("SELECT count(*) FROM observation WHERE source = 'APAC_HIST_DIARIO'")).isEqualTo(625);
        // posto com coordenada e tipo
        Map<String, Object> st = jdbc.queryForMap("SELECT lat, lon, attrs ->> 'tipo' AS tipo FROM station "
                + "WHERE source = 'APAC_HIST_DIARIO' AND station_id = '261160602V'");
        assertThat((Double) st.get("lat")).isEqualTo(-8.0442);
        assertThat(st.get("tipo")).isEqualTo("CONVENCIONAL");
        // P1D fica fora da grade horária
        assertThat(count("SELECT count(*) FROM observation_hourly_v WHERE source = 'APAC_HIST_DIARIO'")).isZero();
        // idempotente
        IngestionRunView again = run();
        assertThat(again.obsInserted()).isZero();
        assertThat(again.obsUpdated()).isZero();
    }

    @Test
    void cabecalhoDiferenteFalhaDeFormaVisivel() {
        when(apacHistorico.daily(any(), any())).thenReturn(
                new ApacHistoricoClient.Response("u", "f", "<html>manutenção</html>".getBytes()));
        IngestionRunView r = run();
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.errorSummary()).contains("APAC histórico");
        assertThat(count("SELECT count(*) FROM observation WHERE source = 'APAC_HIST_DIARIO'")).isZero();
    }

    @Test
    void numeroComVirgulaEAusencia() {
        assertThat(com.projeto.mapi.service.ingestion.adapter.ApacHistoricoAdapterAccess.number("137,40")).isEqualTo(137.4);
        assertThat(com.projeto.mapi.service.ingestion.adapter.ApacHistoricoAdapterAccess.number("-")).isNull();
        assertThat(com.projeto.mapi.service.ingestion.adapter.ApacHistoricoAdapterAccess.number("0,00")).isEqualTo(0.0);
        assertThat(LocalDate.of(2022, 5, 28)).isNotNull();
    }
}

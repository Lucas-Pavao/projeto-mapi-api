package com.projeto.mapi.integration;

import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.AnaLegacyClient;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F2-DADOS-11: chuva do legado da ANA (leitura de 15 min, carimbo no FIM da janela, rótulo em UTC-3) até a grade
 * horária {@code (t-1h, t]} de {@code observation_hourly_v}. Sintético: bordas da hora, janela de 15 min e lacuna.
 */
class AnaChuvaHorariaIntegrationTest extends AbstractIngestionTest {

    private static final String EMPTY = """
            <?xml version="1.0" encoding="utf-8"?>
            <DataTable xmlns="http://MRCS/"><diffgr:diffgram xmlns:diffgr="urn:schemas-microsoft-com:xml-msdata">
            <DocumentElement xmlns=""></DocumentElement></diffgr:diffgram></DataTable>
            """;

    private static String row(String localLabel, String rain) {
        return "<DadosHidrometereologicos><CodEstacao>39187800</CodEstacao><DataHora>" + localLabel
                + " </DataHora><Chuva>" + rain + "</Chuva></DadosHidrometereologicos>";
    }

    /** Rótulos em UTC-3: a hora UTC {@code HH:00Z} é {@code HH-3:00} local. */
    private String series() {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <DataTable xmlns="http://MRCS/"><diffgr:diffgram xmlns:diffgr="urn:schemas-microsoft-com:xml-msdata">
                <DocumentElement xmlns="">
                """
                // hora que termina 12:00Z (09:00 local): 08:15, 08:30, 08:45 e 09:00:00 exato (borda)
                + row("2026-10-01 08:15:00", "0.20") + row("2026-10-01 08:30:00", "0.40")
                + row("2026-10-01 08:45:00", "0.00") + row("2026-10-01 09:00:00", "0.60")
                // 08:00 local (11:00Z) fecha a hora anterior: nunca entra na das 12:00Z
                + row("2026-10-01 08:00:00", "5.00")
                // hora que termina 13:00Z: lacuna (falta 09:30 local) => 3 de 4
                + row("2026-10-01 09:15:00", "1.00") + row("2026-10-01 09:45:00", "1.00")
                + row("2026-10-01 10:00:00", "1.00")
                // hora que termina 14:00Z: quatro leituras zero => 0,0 válido (distinto de ausência)
                + row("2026-10-01 10:15:00", "0.00") + row("2026-10-01 10:30:00", "0.00")
                + row("2026-10-01 10:45:00", "0.00") + row("2026-10-01 11:00:00", "0.00")
                + "</DocumentElement></diffgr:diffgram></DataTable>";
    }

    @BeforeEach
    void stubClient() {
        when(anaLegacy.inventory()).thenReturn(new AnaLegacyClient.Response("inv", EMPTY));
        when(anaLegacy.series(any(), any(), any())).thenReturn(new AnaLegacyClient.Response("u", EMPTY));
        when(anaLegacy.series(eq("39187800"), eq(LocalDate.of(2026, 10, 1)), any()))
                .thenReturn(new AnaLegacyClient.Response("u", series()));
    }

    private Map<String, Object> hour(String hourEnd) {
        return jdbc.queryForMap("SELECT value, n_readings, incomplete FROM observation_hourly_v "
                + "WHERE source = 'ANA_SOAP' AND station_id = '39187800' AND variable = 'precipitation' "
                + "AND hour_end_utc = ?::timestamptz", hourEnd);
    }

    @Test
    void chuvaDeQuinzeMinutosViraHoraComRotuloNoFim() {
        IngestionRunView run = refetch("ANA_SOAP");
        assertThat(run.status()).isEqualTo("OK");

        // a leitura é gravada como PT15M, sem qualificador, com a janela (t-15 min, t] e o instante em UTC
        Map<String, Object> o = jdbc.queryForMap("SELECT resolution, qualifier, ts_utc, interval_start_utc FROM observation "
                + "WHERE ts_utc = '2026-10-01T12:00:00Z' AND variable = 'precipitation'");
        assertThat(o.get("resolution")).isEqualTo("PT15M");
        assertThat(o.get("qualifier")).isNull();
        assertThat(jdbc.queryForObject("SELECT interval_start_utc = '2026-10-01T11:45:00Z'::timestamptz FROM observation "
                + "WHERE ts_utc = '2026-10-01T12:00:00Z' AND variable = 'precipitation'", Boolean.class)).isTrue();

        // borda: a leitura das 09:00:00 local (12:00:00Z) pertence a (11:00Z, 12:00Z], a das 08:00 local não
        Map<String, Object> h12 = hour("2026-10-01T12:00:00Z");
        assertThat((Double) h12.get("value")).isCloseTo(1.2, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(h12.get("n_readings")).isEqualTo(4);
        assertThat(h12.get("incomplete")).isEqualTo(false);
        assertThat(hour("2026-10-01T11:00:00Z").get("n_readings")).isEqualTo(1); // só a das 08:00 local

        // lacuna: 3 de 4 leituras => NULL e incompleta, nunca preenchida
        Map<String, Object> h13 = hour("2026-10-01T13:00:00Z");
        assertThat(h13.get("n_readings")).isEqualTo(3);
        assertThat(h13.get("value")).isNull();
        assertThat(h13.get("incomplete")).isEqualTo(true);

        // zero medido é valor, não ausência
        Map<String, Object> h14 = hour("2026-10-01T14:00:00Z");
        assertThat(h14.get("value")).isEqualTo(0.0);
        assertThat(h14.get("n_readings")).isEqualTo(4);
    }

    @Test
    void reprocessarNaoMudaAHora() {
        refetch("ANA_SOAP");
        Object before = hour("2026-10-01T12:00:00Z").get("value");
        IngestionRunView again = refetch("ANA_SOAP");
        assertThat(again.obsInserted()).isZero();
        assertThat(again.obsUpdated()).isZero();
        assertThat(hour("2026-10-01T12:00:00Z").get("value")).isEqualTo(before);
    }
}

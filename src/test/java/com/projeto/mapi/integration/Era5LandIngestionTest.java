package com.projeto.mapi.integration;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.Era5LandClient;
import com.projeto.mapi.support.AbstractIngestionTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/** ERA5-Land ponta a ponta com resposta SINTÉTICA no formato da API (nenhuma chamada real). */
class Era5LandIngestionTest extends AbstractIngestionTest {

    @Autowired private FloodPointRepository points;

    private static String body(int offset, String v0, String v1, String v2) {
        return """
                {"latitude":-8.0,"longitude":-34.9,"elevation":11.0,"utc_offset_seconds":%d,"timezone":"GMT",
                 "hourly_units":{"time":"iso8601","soil_moisture_0_to_7cm":"m³/m³"},
                 "hourly":{"time":["2026-09-01T23:00","2026-09-02T00:00","2026-09-02T01:00"],
                   "soil_moisture_0_to_7cm":[%s,%s,%s]}}
                """.formatted(offset, v0, v1, v2);
    }

    private void respond(String body) {
        when(era5Land.fetch(anyDouble(), anyDouble(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new Era5LandClient.Response("http://x", body));
    }

    private IngestionRunView run() {
        return pipeline.execute(new IngestionRequest(Era5LandAdapterSource.NAME,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"),
                IngestionMode.REFETCH, IngestionKind.BACKFILL));
    }

    /** Evita repetir o literal da fonte nos testes. */
    private static final class Era5LandAdapterSource {
        static final String NAME = com.projeto.mapi.service.ingestion.adapter.Era5LandAdapter.SOURCE;
    }

    @BeforeEach
    void ponto() {
        jdbc.execute("TRUNCATE flood_point_pluviometers, flood_point_river_levels, flood_point_weather_stations, "
                + "flood_events, flood_points CASCADE");
        points.save(FloodPoint.builder().slug("p1").name("Ponto 1").latitude(-8.05).longitude(-34.88).build());
        respond(body(0, "0.31", "0.32", "0.33"));
    }

    /** T6: janela [from, to) é semiaberta; o registro de 00:00 do dia seguinte fica de fora. */
    @Test
    void bordaDaHoraEJanelaSemiaberta() {
        IngestionRunView r = run();
        assertThat(r.status()).isEqualTo("OK");
        assertThat(r.fetched()).isEqualTo(1); // só 23:00; 00:00 e 01:00 estão fora de [from,to)
        assertThat(r.obsInserted()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT value FROM observation WHERE variable = 'soil_moisture_0_7cm' "
                + "AND ts_utc = '2026-09-01T23:00:00Z'", Double.class)).isEqualTo(0.31);
        // estado instantâneo: a leitura das 23:00 exatas pertence à linha horária 23:00 (não à das 22:00)
        assertThat(jdbc.queryForObject("SELECT value FROM observation_hourly_v WHERE variable = 'soil_moisture_0_7cm' "
                + "AND hour_end_utc = '2026-09-01T23:00:00Z'", Double.class)).isEqualTo(0.31);
        assertThat(jdbc.queryForObject("SELECT unit FROM observation WHERE variable = 'soil_moisture_0_7cm'",
                String.class)).isEqualTo("m³/m³");
        assertThat(count("SELECT count(*) FROM station WHERE source = 'OPEN_METEO_ERA5LAND' "
                + "AND attrs->>'model' = 'era5_land'")).isEqualTo(1);
    }

    /** T1: repetir a mesma carga não cria bruto, observação nem revisão. */
    @Test
    void idempotencia() {
        run();
        IngestionRunView again = run();
        assertThat(again.rawInserted()).isZero();
        assertThat(again.obsInserted()).isZero();
        assertThat(again.obsUpdated()).isZero();
        assertThat(count("SELECT count(*) FROM observation WHERE variable = 'soil_moisture_0_7cm'")).isEqualTo(1);
    }

    @Test
    void foraDaFaixaFisicaVaiParaQuarentenaENuloVirarLacuna() {
        respond(body(0, "1.2", "null", "0.3"));
        IngestionRunView r = run();
        assertThat(r.obsInserted()).isZero();
        assertThat(count("SELECT count(*) FROM ingestion_quarantine WHERE reason = 'OUT_OF_RANGE'")).isEqualTo(1);
    }

    @Test
    void respostaComOffsetDiferenteDeZeroFalha() {
        respond(body(-10800, "0.3", "0.3", "0.3"));
        IngestionRunView r = run();
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.errorSummary()).contains("utc_offset_seconds");
        assertThat(count("SELECT count(*) FROM observation")).isZero();
    }
}

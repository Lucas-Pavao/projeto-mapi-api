package com.projeto.mapi.integration;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.ingestion.IngestionRunView;
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

/**
 * Open-Meteo archive ponta a ponta com resposta SINTÉTICA (formato da API; não é fixture real anonimizada,
 * pendência registrada no relatório).
 */
class OpenMeteoArchiveIngestionTest extends AbstractIngestionTest {

    @Autowired private FloodPointRepository points;

    private static String body(int offset) {
        return """
                {"latitude":-8.05,"longitude":-34.88,"elevation":4.0,"utc_offset_seconds":%d,"timezone":"GMT",
                 "hourly_units":{"time":"iso8601","precipitation":"mm","temperature_2m":"°C","relative_humidity_2m":"%%",
                   "surface_pressure":"hPa","weather_code":"wmo code","wind_speed_10m":"km/h","shortwave_radiation":"W/m²"},
                 "hourly":{"time":["2026-09-01T13:00","2026-09-01T14:00","2026-09-01T15:00"],
                   "precipitation":[0.0,2.5,null],"temperature_2m":[27.1,27.5,28.0],
                   "relative_humidity_2m":[80,79,78],"surface_pressure":[1010.0,1010.5,1011.0],
                   "weather_code":[1,61,61],"wind_speed_10m":[10.0,11.0,12.0],"shortwave_radiation":[500.0,450.0,300.0]}}
                """.formatted(offset);
    }

    @BeforeEach
    void ponto() {
        jdbc.execute("TRUNCATE flood_point_pluviometers, flood_point_river_levels, flood_point_weather_stations, "
                + "flood_events, flood_points CASCADE");
        points.save(FloodPoint.builder().slug("p1").name("Ponto 1").latitude(-8.05).longitude(-34.88).build());
        when(openMeteo.fetch(anyDouble(), anyDouble(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveClient.Response("http://x", body(0)));
    }

    @Test
    void historicoVaiParaOPipelineNovoEmUtc() {
        IngestionRunView run = pipeline.execute(new com.projeto.mapi.service.ingestion.IngestionRequest(
                "OPEN_METEO_ARCHIVE", Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"),
                com.projeto.mapi.service.ingestion.IngestionMode.REFETCH,
                com.projeto.mapi.service.ingestion.IngestionKind.BACKFILL));
        assertThat(run.status()).isEqualTo("OK");
        assertThat(run.fetched()).isEqualTo(3);
        assertThat(run.obsInserted()).isEqualTo(3 * 7 - 1); // precipitação das 15:00 é null: lacuna, sem constante
        assertThat(run.params().path("requests").get(0).path("sha256").asText()).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT value FROM observation WHERE variable = 'precipitation' "
                + "AND ts_utc = '2026-09-01T14:00:00Z'", Double.class)).isEqualTo(2.5);
        assertThat(jdbc.queryForObject("SELECT interval_start_utc = '2026-09-01T13:00:00Z' FROM observation "
                + "WHERE variable = 'precipitation' AND ts_utc = '2026-09-01T14:00:00Z'", Boolean.class)).isTrue();
        assertThat(count("SELECT count(*) FROM station WHERE source = 'OPEN_METEO_ARCHIVE' AND jsonb_exists(attrs, 'grid_lat')")).isEqualTo(1);

        IngestionRunView again = pipeline.execute(new com.projeto.mapi.service.ingestion.IngestionRequest(
                "OPEN_METEO_ARCHIVE", Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"),
                com.projeto.mapi.service.ingestion.IngestionMode.REFETCH,
                com.projeto.mapi.service.ingestion.IngestionKind.BACKFILL));
        assertThat(again.rawInserted()).isZero();
        assertThat(again.obsInserted()).isZero();
        assertThat(again.obsUpdated()).isZero();
    }

    @Test
    void respostaComOffsetDiferenteDeZeroFalhaOExecucao() {
        when(openMeteo.fetch(anyDouble(), anyDouble(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveClient.Response("http://x", body(-10800)));
        IngestionRunView run = pipeline.execute(new com.projeto.mapi.service.ingestion.IngestionRequest(
                "OPEN_METEO_ARCHIVE", Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"),
                com.projeto.mapi.service.ingestion.IngestionMode.REFETCH,
                com.projeto.mapi.service.ingestion.IngestionKind.BACKFILL));
        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(run.errorSummary()).contains("utc_offset_seconds");
        assertThat(count("SELECT count(*) FROM observation")).isZero();
    }
}

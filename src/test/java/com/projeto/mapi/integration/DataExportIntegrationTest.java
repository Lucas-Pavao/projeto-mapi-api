package com.projeto.mapi.integration;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.model.FloodEvent;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.model.WeatherData;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.repository.WeatherDataRepository;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Export do dataset (base do treino da IA) com dados semeados num TimescaleDB real: confere o
 * cabeçalho do CSV, o número de linhas (uma por hora da janela) e o rótulo isFlooded.
 */
class DataExportIntegrationTest extends AbstractTimescaleIntegrationTest {

    private static final String HEADER = "floodPointSlug,timestamp,sensorPrecipitation,sensorWaterLevel,"
            + "sensorSoilHumidity,weatherPrecipitation,weatherTemperature,weatherPressure,weatherCode,tideHeight,"
            + "accumulated3h,accumulated6h,accumulated12h,accumulated24h,accumulated48h,isFlooded,severity,"
            + "timestampUtc,sensorPrecipitation_missing,sensorWaterLevel_missing,sensorSoilHumidity_missing,weatherPrecipitation_missing,"
            + "weatherTemperature_missing,weatherPressure_missing,weatherCode_missing,tideHeight_missing,"
            + "accumulated3h_missing,accumulated6h_missing,accumulated12h_missing,accumulated24h_missing,accumulated48h_missing,label_h6,label_h3,label_day,label_excluded_reason,occurrence_ids,soil_moisture_0_7cm,soil_moisture_0_7cm_missing";

    @Autowired private DataExportService exportService;
    @Autowired private FloodPointRepository pointRepository;
    @Autowired private SensorDataRepository sensorRepository;
    @Autowired private WeatherDataRepository weatherRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private java.util.UUID runId;
    @Autowired private FloodEventRepository eventRepository;

    // A maré vem de API externa; aqui só queremos o export, então um valor fixo.
    @MockitoBean private TideService tideService;

    @BeforeEach
    void semeia() {
        when(tideService.getStoredTideHeightAt(anyDouble(), anyDouble(), any(LocalDateTime.class))).thenReturn(1.23);
        eventRepository.deleteAll();
        sensorRepository.deleteAll();
        weatherRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();

        FloodPoint ponto = pointRepository.save(FloodPoint.builder()
                .slug("ponto-teste").name("Ponto Teste").latitude(-8.05).longitude(-34.88).build());

        LocalDateTime agora = LocalDateTime.now(java.time.ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        sensorRepository.save(SensorData.builder().sensorId("S1").latitude(-8.05).longitude(-34.88)
                .timestamp(agora.minusHours(2)).accumulatedPrecipitation(7.5).build());
        // clima agora vem do pipeline novo (observation_hourly_v), em UTC
        jdbc.execute("TRUNCATE observation_revision, observation, ingestion_quarantine, raw_observation, ingestion_run, station CASCADE");
        java.util.UUID run = java.util.UUID.randomUUID();
        runId = run;
        jdbc.update("INSERT INTO ingestion_run (id, source, kind, mode, code_version, parser_version, rule_version, started_at, status)"
                + " VALUES (?, 'OPEN_METEO_ARCHIVE', 'BACKFILL', 'REFETCH', 'test', 'test', 'test', now(), 'OK')", run);
        java.time.Instant hora = agora.minusHours(1).toInstant(java.time.ZoneOffset.UTC);
        for (Object[] v : new Object[][] {{"precipitation", "mm", 3.0}, {"temperature", "°C", 27.0},
                {"pressure", "hPa", 1010.0}, {"weather_code", "wmo code", 61.0}}) {
            jdbc.update("INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, interval_start_utc,"
                    + " run_id, raw_hash, rule_version, value_hash, origin_ts_norm) VALUES ('OPEN_METEO_ARCHIVE', ?, ?, 'PT1H', ?::timestamptz,"
                    + " ?, ?, ?::timestamptz, ?, 'h', 'test', 'v', 'x')", "-8.0500,-34.8800", v[0], hora.toString(), v[2], v[1],
                    hora.minusSeconds(3600).toString(), run);
        }
        eventRepository.save(FloodEvent.builder().floodPoint(ponto)
                .startTime(agora.minusHours(2)).endTime(agora.minusHours(2)).build());
    }

    @Test
    void csvTemColunasELinhasEsperadas() {
        List<UnifiedDataDTO> dados = exportService.exportUnifiedDataWithAccumulated("ponto-teste", 1);
        // janela de 1 dia, hora a hora, extremos inclusos
        assertThat(dados).hasSize(25);

        String csv = exportService.generateCsv(dados);
        String[] linhas = csv.split("\n");
        assertThat(linhas[0]).isEqualTo(HEADER);
        assertThat(linhas).hasSize(26); // cabeçalho + 25
        assertThat(linhas[1].split(",", -1)).hasSameSizeAs(HEADER.split(","));

        // sem imputação: ausência é célula vazia (nunca o literal "null") e o sinalizador _missing acompanha
        assertThat(csv).doesNotContain("null");
        assertThat(dados).filteredOn(d -> d.getSensorPrecipitation() == null).hasSize(24);
        assertThat(dados).filteredOn(d -> d.getWeatherTemperature() == null).hasSize(24);
        String[] cols = linhas[1].split(",", -1);
        assertThat(cols[17]).endsWith("Z");            // timestampUtc em ISO-8601 com Z
        assertThat(cols[2]).isEmpty();                 // sensorPrecipitation ausente
        assertThat(cols[18]).isEqualTo("1");           // sensorPrecipitation_missing
        assertThat(cols[20]).isEqualTo("1");           // sensorSoilHumidity_missing
        assertThat(dados.get(0).getAccumulated48h()).isNull(); // janela de 48 h incompleta

        assertThat(dados).filteredOn(d -> Boolean.TRUE.equals(d.getIsFlooded())).hasSize(1);
        assertThat(dados).filteredOn(d -> Double.valueOf(7.5).equals(d.getSensorPrecipitation())).hasSize(1);
        assertThat(dados).allSatisfy(d -> assertThat(d.getTideHeight()).isEqualTo(1.23));
    }

    @Test
    void climaVemDaGradeHorariaEManifestoAmarraOCsv() {
        List<UnifiedDataDTO> dados = exportService.exportUnifiedDataWithAccumulated("ponto-teste", 1);
        assertThat(dados).filteredOn(d -> d.getWeatherPrecipitation() != null && d.getWeatherPrecipitation() == 3.0).hasSize(1);

        var export = exportService.exportCsv("ponto-teste", 1, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        assertThat(export.manifest().datasetId()).isEqualTo(com.projeto.mapi.service.ingestion.Hashing.sha256(export.csv()));
        assertThat(export.manifest().timezone()).isEqualTo("UTC");
        assertThat(export.manifest().missingPolicy()).contains("Sem imputação").contains("_missing");
        assertThat(export.manifest().missingCounts().get("weatherTemperature")).isEqualTo(24);
        assertThat(export.manifest().provenance()).anySatisfy(p -> {
            assertThat(p.variable()).isEqualTo("precipitation");
            assertThat(p.runIds()).containsExactly(runId);
        });
    }
}

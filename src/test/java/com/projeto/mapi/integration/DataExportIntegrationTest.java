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
            + "accumulated3h_missing,accumulated6h_missing,accumulated12h_missing,accumulated24h_missing,accumulated48h_missing,label_h6,label_h3,label_day,label_excluded_reason,occurrence_ids,soil_moisture_0_7cm,soil_moisture_0_7cm_missing,"
            + "tide_height_t1,tide_height_t2,tide_height_t3,tide_height_t4,tide_height_t5,tide_height_t6,tide_max_next_6h,"
            + "tide_hours_to_next_high,tide_range_day_m,tide_rising,tide_slope_m_h,"
            + "tide_height_t1_missing,tide_height_t2_missing,tide_height_t3_missing,tide_height_t4_missing,"
            + "tide_height_t5_missing,tide_height_t6_missing,tide_max_next_6h_missing,tide_hours_to_next_high_missing,"
            + "tide_range_day_m_missing,tide_rising_missing,tide_slope_m_h_missing,ana_precipitation,ana_precipitation_missing";

    @Autowired private DataExportService exportService;
    @Autowired private FloodPointRepository pointRepository;
    @Autowired private SensorDataRepository sensorRepository;
    @Autowired private WeatherDataRepository weatherRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private java.util.UUID runId;
    @Autowired private FloodEventRepository eventRepository;

    // O export não usa mais o TideService (maré vem do pipeline); o mock evita chamadas externas de outros beans.
    @MockitoBean private TideService tideService;

    @BeforeEach
    void semeia() {
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
        // maré prevista do pipeline: 1,23 m constante em todas as horas cheias de -2 d a +8 h
        java.util.UUID tideRun = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_run (id, source, kind, mode, code_version, parser_version, rule_version, started_at, status)"
                + " VALUES (?, 'TIDE_PE02_HOURLY', 'BACKFILL', 'REFETCH', 'test', 'test', 'test', now(), 'OK')", tideRun);
        for (int h = -48; h <= 8; h++) {
            java.time.Instant t = agora.plusHours(h).toInstant(java.time.ZoneOffset.UTC);
            jdbc.update("INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, qualifier,"
                    + " interval_start_utc, run_id, raw_hash, rule_version, value_hash, origin_ts_norm)"
                    + " VALUES ('TIDE_PE02_HOURLY', 'pe02', 'tide_height_hourly', 'PT1H', ?::timestamptz, 1.23, 'm',"
                    + " 'INTERP_COSSENO', ?::timestamptz, ?, 'h', 'test', 'v', 'x')", t.toString(), t.toString(), tideRun);
        }
        java.time.Instant hora = agora.minusHours(1).toInstant(java.time.ZoneOffset.UTC);
        for (Object[] v : new Object[][] {{"precipitation", "mm", 3.0}, {"temperature", "°C", 27.0},
                {"pressure", "hPa", 1010.0}, {"weather_code", "wmo code", 61.0}}) {
            jdbc.update("INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, interval_start_utc,"
                    + " run_id, raw_hash, rule_version, value_hash, origin_ts_norm) VALUES ('OPEN_METEO_ARCHIVE', ?, ?, 'PT1H', ?::timestamptz,"
                    + " ?, ?, ?::timestamptz, ?, 'h', 'test', 'v', 'x')", "-8.0500,-34.8800", v[0], hora.toString(), v[2], v[1],
                    hora.minusSeconds(3600).toString(), run);
        }
        // chuva ANA (F2-DADOS-11): pluviômetro mais próximo, 4 leituras de 15 min (0,5 mm) na hora que termina em `hora`
        jdbc.update("INSERT INTO station (source, station_id, name, lat, lon) VALUES ('ANA_SOAP', 'ANA-PERTO', 'perto', -8.10, -34.90),"
                + " ('ANA_SOAP', 'ANA-LONGE', 'longe', -7.50, -35.50)");
        for (String st : new String[] {"ANA-PERTO", "ANA-LONGE"}) {
            for (int k = 0; k < 4; k++) {
                java.time.Instant t = hora.minusSeconds(900L * k);
                jdbc.update("INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, interval_start_utc,"
                        + " run_id, raw_hash, rule_version, value_hash, origin_ts_norm) VALUES ('ANA_SOAP', ?, 'precipitation', 'PT15M',"
                        + " ?::timestamptz, ?, 'mm', ?::timestamptz, ?, 'h', 'test', 'v', 'x')", st, t.toString(),
                        st.equals("ANA-PERTO") ? 0.5 : 9.0, t.minusSeconds(900).toString(), run);
            }
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
        // maré constante: t+1..t+6 presentes, derivada nula => tide_rising vazio; sem extremos => preamar/amplitude vazias
        assertThat(dados).allSatisfy(d -> {
            assertThat(d.getTideHeightAhead()).hasSize(6).containsEntry(6, 1.23);
            assertThat(d.getTideMaxNext6h()).isEqualTo(1.23);
            assertThat(d.getTideSlopeMh()).isEqualTo(0.0);
            assertThat(d.getTideRising()).isNull();
            assertThat(d.getTideHoursToNextHigh()).isNull();
        });
    }

    @Test
    void chuvaAnaVemDoPluviometroMaisProximoEFicaVaziaFora() {
        List<UnifiedDataDTO> dados = exportService.exportUnifiedDataWithAccumulated("ponto-teste", 1);
        assertThat(dados).filteredOn(d -> d.getAnaPrecipitation() != null).singleElement()
                .satisfies(d -> assertThat(d.getAnaPrecipitation()).isEqualTo(2.0)); // 4 x 0,5 mm; a estação distante (9 mm) não entra
        String[] linhas = exportService.generateCsv(dados).split("\n");
        String[] cab = linhas[0].split(",");
        assertThat(cab[cab.length - 2]).isEqualTo("ana_precipitation");
        assertThat(cab[cab.length - 1]).isEqualTo("ana_precipitation_missing");
        assertThat(java.util.Arrays.stream(linhas).skip(1).filter(l -> l.endsWith(",1")).count()).isEqualTo(24);
    }

    @Test
    void climaVemDaGradeHorariaEManifestoAmarraOCsv() {
        List<UnifiedDataDTO> dados = exportService.exportUnifiedDataWithAccumulated("ponto-teste", 1);
        assertThat(dados).filteredOn(d -> d.getWeatherPrecipitation() != null && d.getWeatherPrecipitation() == 3.0).hasSize(1);

        var export = exportService.exportCsv("ponto-teste", 1, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        assertThat(export.manifest().datasetId()).isEqualTo(com.projeto.mapi.service.ingestion.Hashing.sha256(export.csv()));
        assertThat(export.manifest().timezone()).isEqualTo("UTC");
        assertThat(export.manifest().missingPolicy()).contains("Sem imputação").contains("_missing");
        // RC-03: a versão do código vem do arquivo de build com o commit; nunca "unknown" quando ele existe.
        assertThat(export.manifest().codeVersion()).isNotBlank().doesNotStartWith("unknown");
        assertThat(export.manifest().missingCounts().get("weatherTemperature")).isEqualTo(24);
        assertThat(export.manifest().provenance()).anySatisfy(p -> {
            assertThat(p.variable()).isEqualTo("precipitation");
            assertThat(p.runIds()).containsExactly(runId);
        });
    }
}

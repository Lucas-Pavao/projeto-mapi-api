package com.projeto.mapi.integration;

import com.projeto.mapi.dto.CurrentWeatherDTO;
import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.HourlyPrecipitationDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.model.DayData;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.GeoLocation;
import com.projeto.mapi.model.HourData;
import com.projeto.mapi.model.MonthData;
import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.service.MapiService;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.flood.FloodPredictionService;
import com.projeto.mapi.service.flood.PredictionResult;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.tide.TabuaMareService;
import com.projeto.mapi.service.weather.MarineService;
import com.projeto.mapi.service.weather.WeatherService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-BACK-19: o ciclo de predição usa o TideService REAL (não mockado) com uma tábua de marés
 * persistida. Antes, getCurrentTideHeight lia geoLocations/months (LAZY) fora de transação e o ciclo
 * falhava em todo ponto com LazyInitializationException.
 */
class PredictionCycleTideIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired private MapiService mapiService;
    @Autowired private FloodPointRepository pointRepository;
    @Autowired private FloodEventRepository eventRepository;
    @Autowired private FloodPredictionRepository predictionRepository;
    @Autowired private TideTableRepository tideTableRepository;

    @MockitoBean private FloodPredictionService floodPredictionService;
    @MockitoBean private SensorService sensorService;
    @MockitoBean private WeatherService weatherService;
    @MockitoBean private TabuaMareService tabuaMareService;
    @MockitoBean private MarineService marineService;
    @MockitoBean private DataExportService dataExportService;

    @BeforeEach
    void semeia() {
        when(tabuaMareService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(null);
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();
        tideTableRepository.deleteAll();
        pointRepository.save(FloodPoint.builder().slug("ponto-mare").name("Ponto maré")
                .latitude(-8.0631).longitude(-34.8711).build());

        LocalDate hoje = LocalDate.now();
        TideTable tabela = TideTable.builder().year(hoje.getYear()).harborName("PORTO DO RECIFE").state("PE").build();
        GeoLocation geo = GeoLocation.builder().tideTable(tabela).lat("-8.0631").lng("-34.8711").build();
        HourData h1 = HourData.builder().hour("00:00:00").level(0.5f).build();
        HourData h2 = HourData.builder().hour("23:59:00").level(2.5f).build();
        DayData dia = DayData.builder().day(hoje.getDayOfMonth()).hours(List.of(h1, h2)).build();
        h1.setDayData(dia);
        h2.setDayData(dia);
        MonthData mes = MonthData.builder().month(hoje.getMonthValue()).days(List.of(dia)).build();
        dia.setMonthData(mes);
        mes.setTideTable(tabela);
        tabela.setGeoLocations(List.of(geo));
        tabela.setMonths(List.of(mes));
        tideTableRepository.save(tabela);
    }

    @Test
    void cicloCompletaComMareLocalSemLazyInitializationException() {
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(floodPredictionService.predict(any(FloodPredictionRequestDTO.class), anyCollection()))
                .thenReturn(new PredictionResult(FloodPredictionResponseDTO.builder()
                        .riskLevel(RiskLevel.LOW).floodProbability(0.1).modelVersion("m").issuedAt(t)
                        .horizonHours(3).validUntil(t.plus(3, ChronoUnit.HOURS)).lastValidIssuedAt(t).build(), "m+x"));

        int feitos = mapiService.refreshPredictionsForActivePoints();

        assertThat(feitos).as("pontos com previsão gravada").isEqualTo(1);
        assertThat(predictionRepository.count()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> imputed = ArgumentCaptor.forClass(Collection.class);
        verify(floodPredictionService).predict(any(), imputed.capture());
        // A maré veio da tábua local (interpolada), não foi imputada como 0.0.
        assertThat(imputed.getValue()).doesNotContain("tide_level");
    }

    @Test
    void cicloEnviaVariaveisMeteorologicasEDeOndaParaAIa() {
        // A IA exige estas 9 variáveis na inferência e responde 422 se faltarem (ADR 0007 §3). Antes o
        // backend não as enviava e toda previsão do ciclo saía UNKNOWN/INSUFFICIENT_DATA.
        when(weatherService.getWeatherData(anyDouble(), anyDouble())).thenReturn(new WeatherResponseDTO(
                -8.06, -34.87, 0.1, 4.0,
                new CurrentWeatherDTO("2026-10-11T19:00", 26.7, 60.0, 29.1, 1013.9, 1, 0, 0.0, 10.3, 0.0)));
        when(marineService.getCurrentWaveHeight(anyDouble(), anyDouble())).thenReturn(1.12);
        when(marineService.getCurrentWavePeriod(anyDouble(), anyDouble())).thenReturn(6.65);
        when(marineService.getCurrentWaveDirection(anyDouble(), anyDouble())).thenReturn(111.0);
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(floodPredictionService.predict(any(FloodPredictionRequestDTO.class), anyCollection()))
                .thenReturn(new PredictionResult(FloodPredictionResponseDTO.builder()
                        .riskLevel(RiskLevel.LOW).floodProbability(0.1).modelVersion("m").issuedAt(t).build(), "m+x"));

        mapiService.refreshPredictionsForActivePoints();

        ArgumentCaptor<FloodPredictionRequestDTO> req = ArgumentCaptor.forClass(FloodPredictionRequestDTO.class);
        verify(floodPredictionService).predict(req.capture(), anyCollection());
        FloodPredictionRequestDTO r = req.getValue();
        assertThat(r.getTemperature()).isEqualTo(26.7);
        assertThat(r.getApparentTemperature()).isEqualTo(29.1);
        assertThat(r.getHumidity()).isEqualTo(60.0);
        assertThat(r.getPressure()).isEqualTo(1013.9);
        assertThat(r.getWindSpeed()).isEqualTo(10.3);
        assertThat(r.getSolarRadiation()).isEqualTo(0.0);
        assertThat(r.getWaveHeight()).isEqualTo(1.12);
        assertThat(r.getWavePeriod()).isEqualTo(6.65);
        assertThat(r.getWaveDirection()).isEqualTo(111.0);
    }

    @Test
    void semGradeDaReanaliseOsAcumuladosVemDoForecastENaoSaoImputados() {
        java.time.LocalDateTime inicio = java.time.LocalDateTime.parse("2026-10-10T20:00");
        List<String> horas = new java.util.ArrayList<>();
        List<Double> chuva = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) { horas.add(inicio.plusHours(i).toString()); chuva.add(i >= 22 ? 2.0 : 0.5); }
        when(weatherService.getWeatherData(anyDouble(), anyDouble())).thenReturn(new WeatherResponseDTO(
                -8.06, -34.87, 0.1, 4.0,
                new CurrentWeatherDTO("2026-10-11T20:15", 26.7, 60.0, 29.1, 1013.9, 1, 0, 0.0, 10.3, 0.0),
                new HourlyPrecipitationDTO(horas, chuva)));
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        when(floodPredictionService.predict(any(FloodPredictionRequestDTO.class), anyCollection()))
                .thenReturn(new PredictionResult(FloodPredictionResponseDTO.builder()
                        .riskLevel(RiskLevel.LOW).floodProbability(0.1).modelVersion("m").issuedAt(t).build(), "m+x"));

        mapiService.refreshPredictionsForActivePoints();

        ArgumentCaptor<FloodPredictionRequestDTO> req = ArgumentCaptor.forClass(FloodPredictionRequestDTO.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> imputed = ArgumentCaptor.forClass(Collection.class);
        verify(floodPredictionService).predict(req.capture(), imputed.capture());
        // Horas fechadas até 20:15: 20:00 (2,0), 19:00 (2,0), 18:00 (2,0) e 21 horas de 0,5 antes.
        assertThat(req.getValue().getRainfall3hAccumulated()).isEqualTo(6.0);
        assertThat(req.getValue().getRainfall24hAccumulated()).isEqualTo(6.0 + 21 * 0.5);
        assertThat(imputed.getValue()).doesNotContain("rainfall_3h_accumulated", "rainfall_24h_accumulated");
    }
}

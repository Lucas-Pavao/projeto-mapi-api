package com.projeto.mapi.integration;

import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.RiskLevel;
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
}

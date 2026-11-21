package com.projeto.mapi.integration;

import com.projeto.mapi.dto.DegradationReason;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.service.MapiService;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.flood.FeatureCatalog;
import com.projeto.mapi.service.flood.PredictionCoherence;
import com.projeto.mapi.service.flood.PredictionResult;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.tide.TabuaMareService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.service.weather.MarineService;
import com.projeto.mapi.service.weather.WeatherService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR 0007 §7 contra um TimescaleDB real: ler não escreve. GET /api/pontos, GET /api/pontos/{slug} e as leituras
 * ambientais não gravam em flood_predictions. Desde 21/11/2026 o classificador de alagamento saiu: o passo do ciclo
 * de coleta só emite a previsão de chuva (rain_v1) e também não grava em flood_predictions (o histórico fica).
 */
class PredictionSideEffectsIntegrationTest extends AbstractTimescaleIntegrationTest {

    private static final String SLUG = "ponto-contrato";

    @Autowired private MapiService mapiService;
    @Autowired private FloodPointRepository pointRepository;
    @Autowired private FloodEventRepository eventRepository;
    @Autowired private FloodPredictionRepository predictionRepository;
    @Autowired private FeatureCatalog catalog;

    @MockitoBean private SensorService sensorService;
    @MockitoBean private WeatherService weatherService;
    @MockitoBean private TideService tideService;
    @MockitoBean private TabuaMareService tabuaMareService;
    @MockitoBean private MarineService marineService;
    @MockitoBean private DataExportService dataExportService;
    @MockitoBean private com.projeto.mapi.service.flood.PointRainInputService rainInputs;
    @MockitoBean private com.projeto.mapi.service.flood.PointPhysicalInputService physicalInputs;
    @MockitoBean private com.projeto.mapi.service.tide.TideLevelService tideLevel;
    @MockitoBean private com.projeto.mapi.service.flood.RainForecastService rainForecast;

    @BeforeEach
    void semeia() {
        when(rainInputs.inputsAt(anyDouble(), anyDouble(), any())).thenReturn(chuvaMedida(0.5, 1.5));
        when(physicalInputs.inputsAt(anyDouble(), anyDouble(), any())).thenReturn(new com.projeto.mapi.service.flood.PointPhysicalInputs(0.2, -0.3, null, null));
        // Mockito devolve 0.0 (não null) para Double; aqui "sem maré" precisa ser null de verdade.
        when(tideService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(1.2);
        when(tabuaMareService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(null);
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        limparDependentesDePontos();
        pointRepository.deleteAll();
        pointRepository.save(FloodPoint.builder().slug(SLUG).name("Ponto do contrato")
                .latitude(-8.05).longitude(-34.88).build());
    }

    @Test
    void getsNaoGravamEmFloodPredictions() {
        long antes = predictionRepository.count();

        mapiService.getFloodPointBySlug(SLUG);
        mapiService.getAllFloodPoints();
        mapiService.getEnvironmentalData(-8.05, -34.88);
        mapiService.getPreciseData(-8.05, -34.88);

        assertThat(predictionRepository.count()).as("gravações em flood_predictions por GET").isEqualTo(antes);
    }

    @Test
    void semPrevisaoGravadaOGetDevolveNulo() {
        FloodPointResponseDTO ponto = mapiService.getFloodPointBySlug(SLUG);
        assertThat(ponto.getFloodPrediction()).isNull();
    }

    @Test
    void cicloSoEmiteAPrevisaoDeChuva_eNaoGravaMaisEmFloodPredictions() {
        long antes = predictionRepository.count();

        int feitos = mapiService.refreshRainForecastsForActivePoints();

        assertThat(feitos).isEqualTo(1);
        assertThat(predictionRepository.count()).as("o classificador saiu: nenhuma linha nova").isEqualTo(antes);
        verify(rainForecast).forecastAndStore(anyLong(), eq(SLUG), anyDouble(), anyDouble());
    }

    private static com.projeto.mapi.service.flood.PointRainInputs chuvaMedida(Double atual, Double acc3h) {
        String[] ids = {"current_rainfall", "rainfall_3h_accumulated", "rainfall_6h_accumulated", "rainfall_12h_accumulated", "rainfall_24h_accumulated"};
        Double[] v = {atual, acc3h, acc3h == null ? null : 3.0, acc3h == null ? null : 6.0, acc3h == null ? null : 12.0};
        int[] h = {1, 3, 6, 12, 24};
        List<com.projeto.mapi.service.flood.PointRainInputs.Value> values = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            values.add(new com.projeto.mapi.service.flood.PointRainInputs.Value(ids[i], v[i], h[i], v[i] == null ? 0 : h[i],
                    List.of(new com.projeto.mapi.service.flood.PointRainInputs.Station("E1", "Estação 1", "CEMADEN_PED"))));
        }
        return new com.projeto.mapi.service.flood.PointRainInputs("MEASURED", Instant.parse("2026-10-11T23:00:00Z"), v[0], v[1], v[2],
                v[3], v[4], values, Instant.parse("2026-10-11T22:50:00Z"), atual == null ? 0 : 1);
    }
}

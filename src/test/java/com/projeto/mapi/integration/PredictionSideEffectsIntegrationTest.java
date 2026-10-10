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
import com.projeto.mapi.service.flood.FloodPredictionService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR 0007 §7 contra um TimescaleDB real: ler não escreve. GET /api/pontos, GET /api/pontos/{slug} e
 * as leituras ambientais não chamam a IA nem gravam em flood_predictions (antes: 1 gravação por
 * chamada); só o passo do ciclo de coleta (e o POST) gravam, e o GET devolve a última gravada.
 */
class PredictionSideEffectsIntegrationTest extends AbstractTimescaleIntegrationTest {

    private static final String SLUG = "ponto-contrato";

    @Autowired private MapiService mapiService;
    @Autowired private FloodPointRepository pointRepository;
    @Autowired private FloodEventRepository eventRepository;
    @Autowired private FloodPredictionRepository predictionRepository;
    @Autowired private FeatureCatalog catalog;

    @MockitoBean private FloodPredictionService floodPredictionService;
    @MockitoBean private SensorService sensorService;
    @MockitoBean private WeatherService weatherService;
    @MockitoBean private TideService tideService;
    @MockitoBean private TabuaMareService tabuaMareService;
    @MockitoBean private MarineService marineService;
    @MockitoBean private DataExportService dataExportService;

    @BeforeEach
    void semeia() {
        // Mockito devolve 0.0 (não null) para Double; aqui "sem maré" precisa ser null de verdade.
        when(tideService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(null);
        when(tabuaMareService.getCurrentTideHeight(anyDouble(), anyDouble())).thenReturn(null);
        eventRepository.deleteAll();
        predictionRepository.deleteAll();
        pointRepository.deleteAll();
        pointRepository.save(FloodPoint.builder().slug(SLUG).name("Ponto do contrato")
                .latitude(-8.05).longitude(-34.88).build());
    }

    private PredictionResult sucesso(Instant issuedAt) {
        return new PredictionResult(FloodPredictionResponseDTO.builder()
                .riskLevel(RiskLevel.MEDIUM).floodProbability(0.42).modelVersion("xgb-h3-2026.11.0")
                .issuedAt(issuedAt).horizonHours(3).validUntil(issuedAt.plus(3, ChronoUnit.HOURS))
                .lastValidIssuedAt(issuedAt)
                .contributors(List.of(catalog.contributor("tide_height_t1", 0.18)))
                .imputedFeatures(catalog.imputed(List.of("river_level"))).build(),
                "xgb-h3-2026.11.0+a1b2c3d");
    }

    private PredictionResult falha(Instant issuedAt, DegradationReason reason) {
        return new PredictionResult(FloodPredictionResponseDTO.builder()
                .riskLevel(RiskLevel.UNKNOWN).issuedAt(issuedAt).degradationReason(reason)
                .message(reason.message()).build(), null);
    }

    @Test
    void getsNaoChamamAIANemGravamEmFloodPredictions() {
        long antes = predictionRepository.count();

        mapiService.getFloodPointBySlug(SLUG);
        mapiService.getAllFloodPoints();
        mapiService.getEnvironmentalData(-8.05, -34.88);
        mapiService.getPreciseData(-8.05, -34.88);

        assertThat(predictionRepository.count()).as("gravações em flood_predictions por GET").isEqualTo(antes);
        verify(floodPredictionService, never()).predict(any(), anyCollection());
    }

    @Test
    void semPrevisaoGravadaOGetDevolveNulo() {
        FloodPointResponseDTO ponto = mapiService.getFloodPointBySlug(SLUG);
        assertThat(ponto.getFloodPrediction()).isNull();
    }

    @Test
    void cicloGravaUmaLinhaPorPontoAtivoEOGetDevolveAUltima() {
        Instant t1 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(10, ChronoUnit.MINUTES);
        when(floodPredictionService.predict(any(FloodPredictionRequestDTO.class), anyCollection()))
                .thenReturn(sucesso(t1));

        int feitos = mapiService.refreshPredictionsForActivePoints();

        assertThat(feitos).isEqualTo(1);
        assertThat(predictionRepository.count()).isEqualTo(1);
        // Imputações declaradas: sem sensores nem clima, tudo que virou 0.0 é registrado.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> imputed = ArgumentCaptor.forClass(Collection.class);
        verify(floodPredictionService).predict(any(), imputed.capture());
        assertThat(imputed.getValue()).contains("tide_level", "river_level", "rainfall_3h_accumulated",
                "rainfall_6h_accumulated", "rainfall_12h_accumulated", "rainfall_24h_accumulated");

        long antes = predictionRepository.count();
        FloodPredictionResponseDTO lida = mapiService.getFloodPointBySlug(SLUG).getFloodPrediction();
        FloodPredictionResponseDTO daLista = mapiService.getAllFloodPoints().get(0).getFloodPrediction();
        assertThat(predictionRepository.count()).isEqualTo(antes);

        assertThat(PredictionCoherence.violation(lida)).isEmpty();
        assertThat(lida.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(lida.floodProbability()).isEqualTo(0.42);
        assertThat(lida.modelVersion()).isEqualTo("xgb-h3-2026.11.0");
        assertThat(lida.issuedAt()).isEqualTo(t1);
        assertThat(lida.validUntil()).isEqualTo(t1.plus(3, ChronoUnit.HOURS));
        assertThat(lida.lastValidIssuedAt()).isEqualTo(t1);
        assertThat(lida.contributors()).extracting("label").containsExactly("Maré prevista na próxima hora");
        assertThat(lida.imputedFeatures()).extracting("featureId").containsExactly("river_level");
        assertThat(daLista).isEqualTo(lida);
        // A coluna é String: o enum só existe na borda, sem migração.
        assertThat(predictionRepository.findAll().get(0).getRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void falhaDepoisDeSucessoMantemLastValidIssuedAtEGravaProbabilidadeNula() {
        Instant t1 = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(20, ChronoUnit.MINUTES);
        Instant t2 = t1.plus(10, ChronoUnit.MINUTES);
        when(floodPredictionService.predict(any(FloodPredictionRequestDTO.class), anyCollection()))
                .thenReturn(sucesso(t1)).thenReturn(falha(t2, DegradationReason.AI_TIMEOUT));

        mapiService.refreshPredictionsForActivePoints();
        mapiService.refreshPredictionsForActivePoints();

        assertThat(predictionRepository.count()).isEqualTo(2);
        FloodPredictionResponseDTO lida = mapiService.getFloodPointBySlug(SLUG).getFloodPrediction();

        assertThat(PredictionCoherence.violation(lida)).isEmpty();
        assertThat(lida.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(lida.floodProbability()).isNull();
        assertThat(lida.degradationReason()).isEqualTo(DegradationReason.AI_TIMEOUT);
        assertThat(lida.message()).isEqualTo(DegradationReason.AI_TIMEOUT.message());
        assertThat(lida.issuedAt()).isEqualTo(t2);
        assertThat(lida.lastValidIssuedAt()).isEqualTo(t1);
        assertThat(predictionRepository.findFirstByLatitudeAndLongitudeOrderByTimestampDesc(-8.05, -34.88).get()
                .getFloodProbability()).as("falha não grava 0.0").isNull();
    }

    @Test
    void postDeCenarioGravaEDevolveUnknownSemLancar() {
        when(floodPredictionService.predict(any(FloodPredictionRequestDTO.class), anyCollection()))
                .thenReturn(falha(Instant.now(), DegradationReason.AI_UNAVAILABLE));

        FloodPredictionResponseDTO r = mapiService.createFloodPrediction(-8.1, -34.9);

        assertThat(r.riskLevel()).isEqualTo(RiskLevel.UNKNOWN);
        assertThat(r.lastValidIssuedAt()).isNull();
        assertThat(predictionRepository.count()).isEqualTo(1);
    }
}

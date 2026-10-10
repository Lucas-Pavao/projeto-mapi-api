package com.projeto.mapi.service.sensor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.AnaStationDTO;
import com.projeto.mapi.dto.CollectionSummaryDTO;
import com.projeto.mapi.service.sensor.ana.AnaCollectorService;
import com.projeto.mapi.service.sensor.ana.AnaDataClient;
import com.projeto.mapi.service.sensor.ana.impl.AnaCollectorServiceImpl;
import com.projeto.mapi.service.sensor.apac.ApacCollectorService;
import com.projeto.mapi.service.sensor.apac.ApacScrapeClient;
import com.projeto.mapi.service.sensor.apac.impl.ApacCollectorServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "Fonte indisponível" tem que ser distinguível de "0 leituras": antes os fallbacks devolviam
 * List.of() e os dois casos viravam o mesmo 0/0 "com sucesso".
 */
@ExtendWith(MockitoExtension.class)
class CollectionSourceUnavailableTest {

    @Mock private ApacScrapeClient apacClient;
    @Mock private AnaDataClient anaClient;
    @Mock private SensorService sensorService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final AppProperties props = new AppProperties();

    @Test
    void apac_falhaDaFonteViraSourceUnavailable() {
        when(apacClient.fetchRawStations(anyString())).thenThrow(new ResourceAccessException("timeout"));
        var service = new ApacCollectorServiceImpl(apacClient, sensorService, mapper, props);

        CollectionSummaryDTO r = service.collectCemaden();

        assertTrue(r.sourceUnavailable());
        assertEquals(0, r.processed());
    }

    @Test
    void apac_respostaVaziaLegitimaNaoEhIndisponivel() {
        when(apacClient.fetchRawStations(anyString())).thenReturn(List.of());
        var service = new ApacCollectorServiceImpl(apacClient, sensorService, mapper, props);

        CollectionSummaryDTO r = service.collectMeteorologia24h();

        assertFalse(r.sourceUnavailable());
        assertEquals(0, r.processed());
    }

    @Test
    void ana_descobertaFalhandoViraSourceUnavailable() {
        when(anaClient.discoverStations()).thenThrow(new ResourceAccessException("timeout"));
        var service = new AnaCollectorServiceImpl(anaClient, sensorService, mapper, props);

        assertTrue(service.collectAll().sourceUnavailable());
    }

    @Test
    void ana_todasAsEstacoesFalhandoViraSourceUnavailable() {
        when(anaClient.discoverStations()).thenReturn(List.of(
                new AnaStationDTO("1", "A", "R", -8.0, -34.9), new AnaStationDTO("2", "B", "R", -8.1, -34.9)));
        when(anaClient.fetchStationMeasurements(anyString(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        var service = new AnaCollectorServiceImpl(anaClient, sensorService, mapper, props);

        CollectionSummaryDTO r = service.collectAll();

        assertTrue(r.sourceUnavailable());
        assertEquals(2, r.errors());
    }

    @Test
    void ana_falhaParcialNaoMarcaFonteComoIndisponivel() {
        JsonNode leitura = mapper.createObjectNode().put("Chuva_Adotada", 1.0);
        when(anaClient.discoverStations()).thenReturn(List.of(
                new AnaStationDTO("1", "A", "R", -8.0, -34.9), new AnaStationDTO("2", "B", "R", -8.1, -34.9)));
        when(anaClient.fetchStationMeasurements(eq("1"), anyString())).thenReturn(List.of(leitura));
        when(anaClient.fetchStationMeasurements(eq("2"), anyString())).thenThrow(new ResourceAccessException("timeout"));
        var service = new AnaCollectorServiceImpl(anaClient, sensorService, mapper, props);

        CollectionSummaryDTO r = service.collectAll();

        assertFalse(r.sourceUnavailable());
        assertEquals(1, r.processed());
        assertEquals(1, r.errors());
    }

    @Test
    void tarefaAgendada_fonteIndisponivelNaoRenovaIdadeNemContaComoSucesso() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CollectorFreshness freshness = new CollectorFreshness(registry);
        AnaCollectorService ana = mock(AnaCollectorService.class);
        ApacCollectorService apac = mock(ApacCollectorService.class);
        when(ana.collectAll()).thenReturn(CollectionSummaryDTO.unavailable());
        when(apac.collectCemaden()).thenReturn(new CollectionSummaryDTO(3, 0));
        var task = new SensorCollectionTask(ana, apac, registry, freshness);

        task.collectAna();
        task.collectApacCemaden();

        assertEquals(1.0, registry.get("mapi.collector.source.unavailable").tag("source", "ana").counter().count());
        assertNull(registry.find("mapi.collector.source.unavailable").tag("source", "apac_cemaden").counter());
        assertTrue(freshness.ageSeconds("apac_cemaden") < 5.0);
    }
}

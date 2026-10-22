package com.projeto.mapi.service.sensor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.service.ingestion.live.LiveSensorStagingService;
import com.projeto.mapi.service.sensor.apac.ApacEnvelopeMapper;
import com.projeto.mapi.service.sensor.apac.ApacScrapeClient;
import com.projeto.mapi.service.sensor.apac.impl.ApacCollectorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cada leitura do Cemaden é a chuva de um bloco de 10/60 min: o pipeline precisa de todas as leituras da janela
 * rolante, não só da última (que continua sendo a única do fluxo legado).
 */
class ApacCemadenBlocksStagingTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ApacScrapeClient scrape;
    private SensorService sensorService;
    private LiveSensorStagingService staging;
    private ApacCollectorServiceImpl collector;

    @BeforeEach
    void setUp() {
        scrape = mock(ApacScrapeClient.class);
        sensorService = mock(SensorService.class);
        staging = mock(LiveSensorStagingService.class);
        collector = new ApacCollectorServiceImpl(scrape, sensorService, mapper, new AppProperties(), staging,
                new ApacEnvelopeMapper(mapper));
    }

    private JsonNode item(String codigo, String cidade, String dataHora, double chuva) {
        ObjectNode dados = mapper.createObjectNode().put("codestacao", codigo).put("latitude", -8.03)
                .put("longitude", -34.93).put("cidade", cidade).put("nome", "Estação " + codigo)
                .put("tipo", "Pluviométrica").put("chuva", chuva);
        return mapper.createObjectNode().put("Estação", "[CEMADEN] Estação " + codigo).put("Data-hora", dataHora)
                .put("Codigo_gmmc", codigo).put("Dados_completos", dados.toString());
    }

    @SuppressWarnings("unchecked")
    private List<JsonNode> staged(int call) {
        ArgumentCaptor<List<JsonNode>> captor = ArgumentCaptor.forClass(List.class);
        verify(staging, times(call)).stage(eq("CEMADEN"), captor.capture());
        return captor.getAllValues().get(call - 1);
    }

    private static List<String> horas(List<JsonNode> envelopes) {
        return envelopes.stream().map(e -> e.path("codigo").asText() + "@" + e.path("data_hora").asText()).toList();
    }

    @Test
    void entregaTodasAsLeiturasDaJanelaEOLegadoSoAUltima() {
        when(scrape.fetchRawStations("cemaden")).thenReturn(List.of(
                item("261160610A", "RECIFE", "2026-10-21 20:10:00", 0.39),
                item("261160610A", "RECIFE", "2026-10-21 20:20:00", 0.0),
                item("261160610A", "RECIFE", "2026-10-21 21:20:00", 0.2),
                item("260410101A", "CARUARU", "2026-10-21 21:00:00", 5.0)));
        when(staging.stage(eq("CEMADEN"), anyList())).thenReturn(3);

        collector.collectCemaden();

        // fora da RMR não entra; as três leituras de Recife entram
        assertThat(horas(staged(1))).containsExactlyInAnyOrder("261160610A@2026-10-21 20:10:00",
                "261160610A@2026-10-21 20:20:00", "261160610A@2026-10-21 21:20:00");
        verify(sensorService, times(1)).processSensorMessage(anyString());
    }

    @Test
    void cicloSeguinteSoEntregaLeituraNova() {
        JsonNode a = item("261160610A", "RECIFE", "2026-10-21 20:10:00", 0.39);
        JsonNode b = item("261160610A", "RECIFE", "2026-10-21 20:20:00", 0.0);
        JsonNode c = item("261160610A", "RECIFE", "2026-10-21 20:30:00", 0.2);
        when(scrape.fetchRawStations("cemaden")).thenReturn(List.of(a, b), List.of(a, b, c), List.of(a, b, c));
        when(staging.stage(eq("CEMADEN"), anyList())).thenReturn(2, 1);

        collector.collectCemaden();
        collector.collectCemaden();
        assertThat(horas(staged(2))).containsExactly("261160610A@2026-10-21 20:30:00");

        // nada novo: não abre run vazio
        collector.collectCemaden();
        verify(staging, times(2)).stage(eq("CEMADEN"), anyList());
    }

    @Test
    void pipelineQueFalhaNaoAvancaAMarca() {
        JsonNode a = item("261160610A", "RECIFE", "2026-10-21 20:10:00", 0.39);
        when(scrape.fetchRawStations("cemaden")).thenReturn(List.of(a));
        when(staging.stage(eq("CEMADEN"), anyList())).thenReturn(0, 1);

        collector.collectCemaden();
        collector.collectCemaden();

        assertThat(horas(staged(2))).containsExactly("261160610A@2026-10-21 20:10:00");
        verify(staging, never()).stage(eq("APAC_METEO24H"), anyList());
    }
}

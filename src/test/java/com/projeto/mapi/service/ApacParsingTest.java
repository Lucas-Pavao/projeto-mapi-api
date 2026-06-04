package com.projeto.mapi.service;

import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.impl.ApacHistoricalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class ApacParsingTest {

    private ApacHistoricalServiceImpl apacService;
    private SensorDataRepository sensorDataRepository;
    private FloodPointRepository floodPointRepository;

    @BeforeEach
    public void setup() {
        sensorDataRepository = mock(SensorDataRepository.class);
        floodPointRepository = mock(FloodPointRepository.class);
        apacService = new ApacHistoricalServiceImpl(sensorDataRepository, floodPointRepository);
    }

    @Test
    public void testParseApacHtml() {
        String html = "<!DOCTYPE html><html><body>" +
                "<table border='1'>" +
                "<tr>" +
                "<th>Código GMMC</th><th>Identificador</th><th>Município</th><th>Estação</th>" +
                "<th>Latitude</th><th>Longitude</th><th>Microrregião</th><th>Mesorregião</th>" +
                "<th>Bacia</th><th>Ano/Mês</th><th>01</th><th>02</th><th>Acumulado</th>" +
                "</tr>" +
                "<tr>" +
                "<td>260005401V</td><td>198</td><td>Abreu e Lima</td><td>Abreu e Lima [Convencional]</td>" +
                "<td>-7.92810000</td><td>-34.90000000</td><td>Recife</td><td>Metropolitana de Recife</td>" +
                "<td>GL1</td><td>2021/05</td><td>17,10</td><td>6,00</td><td>376,20</td>" +
                "</tr>" +
                "</table></body></html>";

        java.util.List<SensorData> capturedData = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            List<SensorData> batch = invocation.getArgument(0);
            capturedData.addAll(batch);
            return null;
        }).when(sensorDataRepository).saveAll(anyList());

        // Usando reflexão para chamar o método privado de parse
        ReflectionTestUtils.invokeMethod(apacService, "parseNewHtmlTableWithJsoup", html, "APAC-PLUVIO-260005401V", "260005401V");

        assertFalse(capturedData.isEmpty(), "Captured data should not be empty");
        assertEquals(2, capturedData.size());
        
        SensorData first = capturedData.stream()
                .filter(d -> d.getTimestamp().getDayOfMonth() == 1)
                .findFirst().orElse(null);
        
        assertNotNull(first);
        assertEquals("APAC-PLUVIO-260005401V", first.getSensorId());
        assertEquals(17.10, first.getAccumulatedPrecipitation());
        assertEquals("260005401V", first.getCode());
        assertEquals(LocalDateTime.of(2021, 5, 1, 0, 0), first.getTimestamp());
    }
}

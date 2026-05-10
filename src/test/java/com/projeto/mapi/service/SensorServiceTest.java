package com.projeto.mapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.impl.SensorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {

    @Mock
    private SensorDataRepository sensorDataRepository;

    private SensorServiceImpl sensorService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sensorService = new SensorServiceImpl(sensorDataRepository, objectMapper);
    }

    @Test
    void shouldProcessJsonPayloadCorrectly() {
        String payload = "{" +
                "\"id_sensor\": \"ANA_123\"," +
                "\"timestamp_coleta\": \"2026-05-10T10:00:00\"," +
                "\"status_bateria\": \"95.5%\"," +
                "\"fog_valor_referencia\": 12.5," +
                "\"dados_originais\": {\"Chuva_Adotada\": 12.5, \"Outro\": \"dado\"}" +
                "}";

        sensorService.processSensorMessage(payload);

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertEquals("ANA_123", savedData.getSensorId());
        assertEquals(12.5, savedData.getValue());
        assertEquals("95.5%", savedData.getBatteryStatus());
        assertEquals("mm", savedData.getUnit());
        assertNotNull(savedData.getRawData());
        assertTrue(savedData.getRawData().contains("Chuva_Adotada"));
    }

    @Test
    void shouldHandleInvalidTimestamp() {
        String payload = "{" +
                "\"id_sensor\": \"APAC_001\"," +
                "\"timestamp_coleta\": \"invalid-date\"," +
                "\"status_bateria\": \"80%\"," +
                "\"fog_valor_referencia\": 5.0," +
                "\"dados_originais\": {\"chuva_acumulada\": 5.0}" +
                "}";

        sensorService.processSensorMessage(payload);

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertNotNull(savedData.getTimestamp());
        assertEquals("mm", savedData.getUnit());
    }
}

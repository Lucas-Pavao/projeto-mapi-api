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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDataFormatTest {

    @Mock
    private SensorDataRepository sensorDataRepository;

    private SensorServiceImpl sensorService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sensorService = new SensorServiceImpl(sensorDataRepository, objectMapper);
        when(sensorDataRepository.findBySensorIdAndTimestamp(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void shouldProcessApacMeteoFormat() {
        String payload = "{\"id_sensor\": \"APAC-METEO-RMR-ETA\", \"timestamp_coleta\": \"2026-05-16T18:58:23.977080\", \"status_bateria\": \"100.0%\", \"fog_valor_referencia\": 126.2, \"dados_originais\": {\"estacao_nome\": \"ETA Castelo Branco\", \"codigo\": \"260790101M\", \"data_hora\": \"2026-05-16 17:05:00\", \"latitude\": null, \"longitude\": null, \"municipio\": \"Não informada\", \"temperatura_ar\": \"52.9\", \"umidade_relativa\": \"65\", \"pressao_atmosferica\": null, \"velocidade_vento\": null, \"direcao_vento\": null, \"radiacao_solar\": \"2798\", \"precipitacao_acumulada\": 126.2, \"umidade_solo\": [\"0.84\", \"2.48\", \"1.25\", null], \"tipo\": \"Mista/N/A\", \"fonte\": \"APAC/Meteorologia24h\"}}";

        sensorService.processSensorMessage(payload);

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertEquals("APAC-METEO-RMR-ETA", savedData.getSensorId());
        assertEquals(126.2, savedData.getValue());
        assertEquals("100.0%", savedData.getBatteryStatus());
        assertEquals("ETA Castelo Branco", savedData.getStationName());
        assertEquals("APAC/Meteorologia24h", savedData.getSource());
    }

    @Test
    void shouldProcessAnaTeleFormat() {
        String payload = "{\"id_sensor\": \"ANA-TELE-CAPIBARIBE\", \"timestamp_coleta\": \"2026-05-16T18:58:27.494447\", \"status_bateria\": \"100.0%\", \"fog_valor_referencia\": null, \"dados_originais\": {\"status\": \"OK\", \"code\": 200, \"message\": \"Sucesso\", \"items\": [{\"Chuva_Adotada\": \"0.00\", \"Chuva_Adotada_Status\": \"0\", \"Cota_Adotada\": \"226.00\", \"Cota_Adotada_Status\": \"0\", \"Data_Atualizacao\": \"2026-05-16 03:23:25.94\", \"Data_Hora_Medicao\": \"2026-05-16 03:00:00.0\", \"Vazao_Adotada\": \"20.94\", \"Vazao_Adotada_Status\": \"0\", \"codigoestacao\": \"39187800\", \"Latitude\": \"-7.9986\", \"Longitude\": \"-34.0392\", \"Estacao_Nome\": \"SÃO LOURENÇO DA MATA II\", \"Bacia_Nome\": \"ATLÂNTICO,TRECHO NORTE/NORDESTE\", \"Municipio_Nome\": \"SÃO LOURENÇO DA MATA\"}]}}";

        sensorService.processSensorMessage(payload);

        // O ANA format processa múltiplos itens, mas no payload só tem 1 item no array
        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository, times(1)).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertEquals("ANA-TELE-CAPIBARIBE", savedData.getSensorId());
        assertEquals(0.0, savedData.getValue()); // Chuva_Adotada é 0.00
        assertEquals("-7.9986", String.valueOf(savedData.getLatitude()));
        assertEquals("-34.0392", String.valueOf(savedData.getLongitude()));
        assertEquals("SÃO LOURENÇO DA MATA II", savedData.getStationName());
        assertEquals("SÃO LOURENÇO DA MATA", savedData.getMunicipality());
    }

    @Test
    void shouldProcessAnaTeleFlowFormat() {
        String payload = "{\"id_sensor\": \"ANA-TELE-CAPIBARIBE-FLOW\", \"timestamp_coleta\": \"2026-05-16T18:58:27.494447\", \"status_bateria\": \"100.0%\", \"fog_valor_referencia\": null, \"dados_originais\": {\"items\": [{\"Vazao_Adotada\": \"20.94\", \"Data_Hora_Medicao\": \"2026-05-16 03:00:00.0\", \"codigoestacao\": \"39187800\"}]}}";

        sensorService.processSensorMessage(payload);

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertEquals(20.94, savedData.getValue());
        assertEquals("m³/s", savedData.getUnit());
    }
}

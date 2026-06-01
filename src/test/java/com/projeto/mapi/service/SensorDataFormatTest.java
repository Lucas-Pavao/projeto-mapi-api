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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDataFormatTest {

    @Mock
    private SensorDataRepository sensorDataRepository;

    @Mock
    private TideService tideService;

    private SensorServiceImpl sensorService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sensorService = new SensorServiceImpl(sensorDataRepository, objectMapper, tideService);
        when(sensorDataRepository.findBySensorIdAndTimestamp(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void shouldProcessApacMeteoFormat() {
        String payload = "{\"id_sensor\": \"APAC-METEO-JABOATAO-ENGENHO\", \"timestamp_coleta\": \"2026-05-20T19:12:06.152686\", \"status_bateria\": \"100.0%\", \"fog_valor_referencia\": 0.0, \"estacao_nome\": \"[CEMADEN] Engenho Velho [H]\", \"codigo\": \"260790119H\", \"data_hora\": \"2026-05-20 19:10:00\", \"latitude\": null, \"longitude\": null, \"municipio\": \"JABOATAO DOS GUARARAPES\", \"temperatura_ar\": null, \"umidade_relativa\": null, \"pressao_atmosferica\": null, \"velocidade_vento\": null, \"direcao_vento\": null, \"radiacao_solar\": null, \"precipitacao_acumulada\": 0.0, \"umidade_solo\": [null, null, null, null], \"tipo\": \"Mista/N/A\", \"fonte\": \"APAC/Meteorologia24h\"}";

        sensorService.processSensorMessage(payload);

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertEquals("APAC-METEO-JABOATAO-ENGENHO", savedData.getSensorId());
        assertEquals(0.0, savedData.getValue());
        assertEquals("100.0%", savedData.getBatteryStatus());
        assertEquals("[CEMADEN] Engenho Velho [H]", savedData.getStationName());
        assertEquals("APAC/Meteorologia24h", savedData.getSource());
    }

    @Test
    void shouldProcessAnaTeleFormat() {
        String payload = "{\"id_sensor\": \"ANA-TELE-CAPIBARIBE\", \"timestamp_coleta\": \"2026-05-20T19:14:46.585529\", \"status_bateria\": \"100.0%\", \"fog_valor_referencia\": 0.2, \"Chuva_Adotada\": \"0.20\", \"Chuva_Adotada_Status\": \"0\", \"Cota_Adotada\": \"266.00\", \"Cota_Adotada_Status\": \"0\", \"Data_Atualizacao\": \"2026-05-20 03:23:26.747\", \"Data_Hora_Medicao\": \"2026-05-20 03:15:00.0\", \"Vazao_Adotada\": \"49.24\", \"Vazao_Adotada_Status\": \"0\", \"codigoestacao\": \"39187800\", \"Latitude\": \"-7.9986\", \"Longitude\": \"-35.0392\", \"Estacao_Nome\": \"S\u00c3O LOUREN\u00c7O DA MATA II\", \"Bacia_Nome\": \"ATL\u00c2NTICO,TRECHO NORTE/NORDESTE\", \"Municipio_Nome\": \"S\u00c3O LOUREN\u00c7O DA MATA\"}";

        sensorService.processSensorMessage(payload);

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertEquals("ANA-TELE-CAPIBARIBE", savedData.getSensorId());
        assertEquals(0.2, savedData.getFogValueReference());
        assertEquals(0.2, savedData.getAccumulatedPrecipitation());
        assertEquals(266.00, savedData.getWaterLevel());
        assertEquals(49.24, savedData.getFlowRate());
        assertEquals(-7.9986, savedData.getLatitude());
        assertEquals(-35.0392, savedData.getLongitude());
        assertEquals("S\u00c3O LOUREN\u00c7O DA MATA II", savedData.getStationName());
    }

    @Test
    void shouldProcessApacPluvioFormat() {
        String payload = "{\"id_sensor\": \"APAC-PLUVIO-RECIFE-GUABIRABA\", \"timestamp_coleta\": \"2026-05-20T19:12:06.927427\", \"status_bateria\": \"100.0%\", \"fog_valor_referencia\": 0.0, \"estacao_nome\": \"[APAC] Guabiraba\", \"codigo\": \"261160612A\", \"data_hora\": \"2026-05-20 18:20:00\", \"latitude\": -7.994, \"longitude\": -34.936, \"municipio\": \"RECIFE\", \"chuva_acumulada\": 0.0, \"tipo\": \"Pluviom\u00e9trica\", \"fonte\": \"APAC/Cemaden\"}";

        sensorService.processSensorMessage(payload);

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(sensorDataRepository).save(captor.capture());

        SensorData savedData = captor.getValue();
        assertEquals("APAC-PLUVIO-RECIFE-GUABIRABA", savedData.getSensorId());
        assertEquals(0.0, savedData.getAccumulatedPrecipitation());
        assertEquals("mm", savedData.getUnit());
    }
}

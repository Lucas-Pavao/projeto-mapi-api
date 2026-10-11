package com.projeto.mapi.service;

import com.projeto.mapi.model.FloodEvent;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.model.WeatherData;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.repository.WeatherDataRepository;
import com.projeto.mapi.service.flood.CivilDefenseService;
import com.projeto.mapi.service.sensor.ana.AnaHistoricalService;
import com.projeto.mapi.service.sensor.apac.ApacHistoricalService;
import com.projeto.mapi.service.sensor.impl.HistoricalDataServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O full-sync não pode mais mexer no horário dos eventos: o realinhamento ao pico de chuva tornava
 * o rótulo derivado da feature (rótulo circular). Um evento da Defesa Civil às 00:00 deve continuar
 * às 00:00 mesmo havendo um pico de chuva às 15:00 no mesmo dia, dentro do raio do ponto.
 */
@ExtendWith(MockitoExtension.class)
class FloodEventLabelPreservationTest {

    @Mock private FloodPointRepository floodPointRepository;
    @Mock private WeatherDataRepository weatherDataRepository;
    @Mock private FloodEventRepository floodEventRepository;
    @Mock private SensorDataRepository sensorDataRepository;
    @Mock private AnaHistoricalService anaHistoricalService;
    @Mock private ApacHistoricalService apacHistoricalService;
    @Mock private CivilDefenseService civilDefenseService;
    @Mock private com.projeto.mapi.service.MapiService mapiService;

    @InjectMocks private HistoricalDataServiceImpl service;

    @Test
    void fullSync_naoAlteraEventoDasZeroHora() {
        LocalDateTime meiaNoite = LocalDateTime.of(2024, 5, 10, 0, 0);
        FloodPoint ponto = FloodPoint.builder().id(1L).slug("ponto").name("Ponto")
                .latitude(-8.05).longitude(-34.88).build();
        FloodEvent evento = FloodEvent.builder().id(1L).floodPoint(ponto).startTime(meiaNoite).build();

        SensorData picoDeChuva = SensorData.builder().sensorId("S").latitude(-8.05).longitude(-34.88)
                .timestamp(meiaNoite.withHour(15)).accumulatedPrecipitation(40.0).build();

        when(floodPointRepository.findAll()).thenReturn(List.of(ponto));
        lenient().when(floodPointRepository.findBySlug("ponto")).thenReturn(Optional.of(ponto));
        // Mais de 8000 registros no ano faz o passo de clima pular a chamada HTTP ao Open-Meteo.
        lenient().when(weatherDataRepository.findByLatitudeAndLongitudeAndTimestampBetween(
                anyDouble(), anyDouble(), any(), any())).thenReturn(Collections.nCopies(8001, new WeatherData()));
        lenient().when(sensorDataRepository.findSensorsByRadius(anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(List.of(picoDeChuva));
        lenient().when(floodEventRepository.findAll()).thenReturn(List.of(evento));

        service.ingestHistoricalData(0);

        assertEquals(meiaNoite, evento.getStartTime());
        assertEquals(null, evento.getEndTime());
        verify(floodEventRepository, never()).save(any());
        verify(floodEventRepository, never()).findAll();
        verify(civilDefenseService).ingestLastYears(0);
    }
}

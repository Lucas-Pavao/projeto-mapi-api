package com.projeto.mapi.service;

import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.impl.HistoricalDataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoricalDataServiceTest {

    @Mock
    private FloodPointRepository floodPointRepository;

    @Mock
    private SensorDataRepository sensorDataRepository;

    @InjectMocks
    private HistoricalDataServiceImpl historicalDataService;

    @Test
    void shouldMapNearestStationToFloodPoint() {
        // Arrange
        FloodPoint point = FloodPoint.builder()
                .slug("PONTO_TESTE")
                .latitude(-8.05)
                .longitude(-34.90)
                .build();

        SensorData stationFar = SensorData.builder()
                .sensorId("FAR_STATION")
                .latitude(-9.0)
                .longitude(-35.0)
                .build();

        SensorData stationNear = SensorData.builder()
                .sensorId("NEAR_STATION")
                .latitude(-8.051)
                .longitude(-34.901)
                .build();

        when(floodPointRepository.findAll()).thenReturn(List.of(point));
        when(sensorDataRepository.findAllLatest(any(java.time.LocalDateTime.class))).thenReturn(List.of(stationFar, stationNear));

        // Act
        historicalDataService.repairStationMappings();

        // Assert
        verify(floodPointRepository, times(1)).save(point);
        org.junit.jupiter.api.Assertions.assertTrue(point.getPluviometerStationIds().contains("NEAR_STATION"));
    }

    @Test
    void shouldNotMapStationIfTooFar() {
        // Arrange
        FloodPoint point = FloodPoint.builder()
                .slug("PONTO_TESTE")
                .latitude(-8.05)
                .longitude(-34.90)
                .build();

        SensorData stationFar = SensorData.builder()
                .sensorId("FAR_STATION")
                .latitude(0.0)
                .longitude(0.0)
                .build();

        when(floodPointRepository.findAll()).thenReturn(List.of(point));
        when(sensorDataRepository.findAllLatest(any(java.time.LocalDateTime.class))).thenReturn(List.of(stationFar));

        // Act
        historicalDataService.repairStationMappings();

        // Assert
        verify(floodPointRepository, atLeastOnce()).save(point);
        org.junit.jupiter.api.Assertions.assertTrue(point.getPluviometerStationIds().isEmpty());
    }
}

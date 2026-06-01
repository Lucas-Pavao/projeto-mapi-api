package com.projeto.mapi.service;

import com.projeto.mapi.dto.CurrentWeatherDTO;
import com.projeto.mapi.dto.MapiResponseDTO;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.service.impl.MapiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapiServiceTest {

    @Mock
    private SensorService sensorService;

    @Mock
    private WeatherService weatherService;

    @Mock
    private TideService tideService;

    @Mock
    private TabuaMareService tabuaMareService;

    @Mock
    private MarineService marineService;

    @Mock
    private com.projeto.mapi.repository.FloodPointRepository floodPointRepository;

    @InjectMocks
    private MapiServiceImpl mapiService;

    private WeatherResponseDTO mockWeather;
    private List<SensorResponseDTO> mockSensors;

    @BeforeEach
    void setUp() {
        mockWeather = new WeatherResponseDTO(
                -8.05, -34.88, 1.0, 10.0,
                new CurrentWeatherDTO("2026-05-18T20:00", 25.0, 80.0, 26.0, 1013.0, 1, 1, 5.0)
        );

        mockSensors = List.of(
                SensorResponseDTO.builder()
                        .sensorId("SENSOR_01")
                        .accumulatedPrecipitation(10.0)
                        .unit("mm")
                        .latitude(-8.06)
                        .longitude(-34.89)
                        .timestamp(LocalDateTime.now())
                        .type("Precipitação")
                        .build(),
                SensorResponseDTO.builder()
                        .sensorId("SENSOR_02")
                        .accumulatedPrecipitation(20.0)
                        .unit("mm")
                        .latitude(-9.0)
                        .longitude(-35.0)
                        .timestamp(LocalDateTime.now())
                        .type("Precipitação")
                        .build()
        );
    }

    @Test
    void shouldReturnSensorDataWhenSensorIsClose() {
        when(weatherService.getWeatherData(anyDouble(), anyDouble())).thenReturn(mockWeather);
        when(sensorService.getAllLatestData()).thenReturn(mockSensors);

        MapiResponseDTO response = mapiService.getPreciseData(-8.055, -34.885);

        assertNotNull(response);
        assertTrue(response.getPreciseData().getSource().contains("Local Sensor Priority"));
        assertEquals("SENSOR_01", response.getNearestSensor().getSensorId());
        assertEquals(10.0, response.getPreciseData().getPrecipitation());
        assertTrue(response.getDistanceToNearestSensorKm() < 2.0);
    }

    @Test
    void shouldReturnWeatherServiceDataWhenNoSensorIsClose() {
        when(weatherService.getWeatherData(anyDouble(), anyDouble())).thenReturn(mockWeather);
        when(sensorService.getAllLatestData()).thenReturn(mockSensors);

        // Location far from mock sensors (more than 30km)
        MapiResponseDTO response = mapiService.getPreciseData(0.0, 0.0);

        assertNotNull(response);
        assertEquals("OPEN_METEO", response.getPreciseData().getSource());
        assertEquals(5.0, response.getPreciseData().getPrecipitation());
        assertTrue(response.getDistanceToNearestSensorKm() > 1000.0);
    }

    @Test
    void shouldNotMapSensorDuringRegistrationIfTooFar() {
        when(sensorService.getAllLatestData()).thenReturn(mockSensors);
        when(weatherService.getWeatherData(anyDouble(), anyDouble())).thenReturn(mockWeather);
        
        com.projeto.mapi.dto.FloodPointRequestDTO request = com.projeto.mapi.dto.FloodPointRequestDTO.builder()
                .id_ponto("FAR_POINT")
                .nome("Ponto Distante")
                .latitude(0.0)
                .longitude(0.0)
                .build();

        when(floodPointRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArguments()[0]);

        com.projeto.mapi.dto.FloodPointResponseDTO response = mapiService.createFloodPoint(request);

        assertNull(response.getConfig_sensores().getEstacao_pluviometrica_id());
        assertNull(response.getConfig_sensores().getEstacao_nivel_rio_id());
    }
}

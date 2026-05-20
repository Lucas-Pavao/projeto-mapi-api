package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.FloodPointRequestDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.MapiResponseDTO;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.MapiService;
import com.projeto.mapi.service.SensorService;
import com.projeto.mapi.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapiServiceImpl implements MapiService {

    private final SensorService sensorService;
    private final WeatherService weatherService;
    private final FloodPointRepository floodPointRepository;

    @Override
    public MapiResponseDTO getPreciseData(double latitude, double longitude) {
        log.info("Buscando dados precisos para lat: {}, lon: {}", latitude, longitude);
        
        WeatherResponseDTO weatherData = weatherService.getWeatherData(latitude, longitude);
        List<SensorResponseDTO> sensors = sensorService.getAllLatestData();

        SensorResponseDTO nearestSensor = findNearestSensor(latitude, longitude, sensors);
        Double distance = null;
        if (nearestSensor != null) {
            distance = calculateDistance(latitude, longitude, nearestSensor.getLatitude(), nearestSensor.getLongitude());
            log.info("Sensor mais próximo encontrado: {} a {} km", nearestSensor.getSensorId(), String.format("%.2f", distance));
        } else {
            log.warn("Nenhum sensor com localização encontrado no sistema.");
        }

        MapiResponseDTO.PreciseData preciseData = determinePreciseData(weatherData, nearestSensor, distance);

        return MapiResponseDTO.builder()
                .requestedLatitude(latitude)
                .requestedLongitude(longitude)
                .preciseData(preciseData)
                .nearestSensor(nearestSensor)
                .openMeteoData(weatherData)
                .distanceToNearestSensorKm(distance)
                .build();
    }

    @Override
    @Transactional
    public FloodPointResponseDTO createFloodPoint(FloodPointRequestDTO request) {
        log.info("Criando novo ponto de alagamento: {}", request.getName());
        FloodPoint floodPoint = FloodPoint.builder()
                .name(request.getName())
                .description(request.getDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .alertThresholdMm(request.getAlertThresholdMm())
                .active(true)
                .build();
        
        floodPoint = floodPointRepository.save(floodPoint);
        return convertToResponseDTO(floodPoint);
    }

    @Override
    public List<FloodPointResponseDTO> getAllFloodPoints() {
        return floodPointRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .toList();
    }

    private FloodPointResponseDTO convertToResponseDTO(FloodPoint fp) {
        return FloodPointResponseDTO.builder()
                .id(fp.getId())
                .name(fp.getName())
                .description(fp.getDescription())
                .latitude(fp.getLatitude())
                .longitude(fp.getLongitude())
                .alertThresholdMm(fp.getAlertThresholdMm())
                .active(fp.getActive())
                .build();
    }

    private SensorResponseDTO findNearestSensor(double lat, double lon, List<SensorResponseDTO> sensors) {
        return sensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .min(Comparator.comparingDouble(s -> calculateDistance(lat, lon, s.getLatitude(), s.getLongitude())))
                .orElse(null);
    }

    private MapiResponseDTO.PreciseData determinePreciseData(WeatherResponseDTO weather, SensorResponseDTO sensor, Double distance) {
        MapiResponseDTO.PreciseData.PreciseDataBuilder builder = MapiResponseDTO.PreciseData.builder();
        
        // Padrão: Dados do Open-Meteo
        builder.source("OPEN_METEO");
        builder.timestamp(LocalDateTime.now());
        
        if (weather != null && weather.current() != null) {
            builder.precipitation(weather.current().precipitation());
            builder.temperature(weather.current().temperature());
            builder.humidity((double) weather.current().humidity());
            
            try {
                if (weather.current().time() != null) {
                    builder.timestamp(LocalDateTime.parse(weather.current().time(), DateTimeFormatter.ISO_DATE_TIME));
                }
            } catch (Exception e) {
                log.warn("Erro ao parsear timestamp da Open-Meteo");
            }
        }

        // Unidades padrão
        builder.unitPrecipitation("mm");
        builder.unitTemperature("°C");
        builder.unitWaterLevel("m");

        // Prioridade: Sensor Local (se estiver a menos de 30km)
        if (sensor != null && (distance == null || distance < 30.0)) {
            builder.source("MIXED (Local Sensor Priority)");
            builder.message("Dados otimizados: Sensores locais encontrados a " + String.format("%.2f", distance) + " km.");
            
            // Sobrescrever com dados reais do sensor se disponíveis
            if (sensor.getAccumulatedPrecipitation() != null) builder.precipitation(sensor.getAccumulatedPrecipitation());
            else if (sensor.getValue() != null && "mm".equals(sensor.getUnit())) builder.precipitation(sensor.getValue());

            if (sensor.getTemperature() != null) builder.temperature(sensor.getTemperature());
            if (sensor.getHumidity() != null) builder.humidity(sensor.getHumidity());
            if (sensor.getPressure() != null) builder.pressure(sensor.getPressure());
            if (sensor.getWindSpeed() != null) builder.windSpeed(sensor.getWindSpeed());
            
            // Adicionar dados que o Open-Meteo não tem (Nível e Vazão)
            if (sensor.getWaterLevel() != null) builder.waterLevel(sensor.getWaterLevel());
            if (sensor.getFlowRate() != null) builder.flowRate(sensor.getFlowRate());
            
            // Usar o timestamp do sensor se for mais recente ou relevante
            if (sensor.getTimestamp() != null) {
                builder.timestamp(sensor.getTimestamp());
            }
        } else if (sensor != null) {
            builder.message("Dados baseados em Open-Meteo. Sensor mais próximo (" + sensor.getSensorId() + ") está muito distante (" + String.format("%.2f", distance) + " km).");
        } else {
            builder.message("Dados baseados em Open-Meteo. Nenhum sensor local encontrado.");
        }

        return builder.build();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Raio da Terra em km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

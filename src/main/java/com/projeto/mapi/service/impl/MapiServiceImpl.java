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
import com.projeto.mapi.service.TideService;
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
    private final TideService tideService;
    private final com.projeto.mapi.service.TabuaMareService tabuaMareService;
    private final com.projeto.mapi.service.MarineService marineService;
    private final FloodPointRepository floodPointRepository;

    private static final double MAX_SENSOR_RADIUS_KM = 20.0;

    @Override
    public MapiResponseDTO getPreciseData(double latitude, double longitude) {
        log.info("Buscando dados precisos para lat: {}, lon: {}", latitude, longitude);
        
        WeatherResponseDTO weatherData = weatherService.getWeatherData(latitude, longitude);
        List<SensorResponseDTO> sensors = sensorService.getAllLatestData();
        Double tideHeight = tideService.getCurrentTideHeight(latitude, longitude);
        Double tideTabuaMare = tabuaMareService.getCurrentTideHeight(latitude, longitude);
        Double waveHeight = marineService.getCurrentWaveHeight(latitude, longitude);
        Double waveDirection = marineService.getCurrentWaveDirection(latitude, longitude);
        Double wavePeriod = marineService.getCurrentWavePeriod(latitude, longitude);

        SensorResponseDTO nearestSensor = findNearestSensor(latitude, longitude, sensors);
        Double distance = null;
        if (nearestSensor != null) {
            distance = calculateDistance(latitude, longitude, nearestSensor.getLatitude(), nearestSensor.getLongitude());
            log.info("Sensor mais próximo encontrado: {} a {} km", nearestSensor.getSensorId(), String.format("%.2f", distance));
        } else {
            log.warn("Nenhum sensor com localização encontrado no sistema.");
        }

        MapiResponseDTO.PreciseData preciseData = determinePreciseData(weatherData, nearestSensor, distance, tideHeight, tideTabuaMare, waveHeight, waveDirection, wavePeriod);

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
        log.info("Criando novo ponto de alagamento com hiper-automação: {}", request.getNome());
        
        // 1. Obter Altitude automaticamente via Open-Meteo
        Double altitude = request.getAltitude_m();
        try {
            WeatherResponseDTO weather = weatherService.getWeatherData(request.getLatitude(), request.getLongitude());
            if (weather != null && altitude == null) {
                altitude = weather.elevation();
                log.info("Altitude obtida automaticamente: {}m", altitude);
            }
        } catch (Exception e) {
            log.warn("Não foi possível obter altitude automaticamente para o ponto {}", request.getNome());
        }

        // 2. Mapeamento de Sensores (Inclui ANA, APAC, CEMADEN)
        SensorResponseDTO nearestRain = findNearestSensorByType(request.getLatitude(), request.getLongitude(), "PRECIPITATION");
        SensorResponseDTO nearestRiver = findNearestSensorByType(request.getLatitude(), request.getLongitude(), "RIVER_LEVEL");

        String pluviometerId = (request.getConfig_sensores() != null && request.getConfig_sensores().getEstacao_pluviometrica_id() != null) 
                ? request.getConfig_sensores().getEstacao_pluviometrica_id() 
                : (nearestRain != null ? nearestRain.getSensorId() : null);

        String riverLevelId = (request.getConfig_sensores() != null && request.getConfig_sensores().getEstacao_nivel_rio_id() != null)
                ? request.getConfig_sensores().getEstacao_nivel_rio_id()
                : (nearestRiver != null ? nearestRiver.getSensorId() : null);

        // 3. Inferir Município se não fornecido
        String municipio = request.getMunicipio();
        if (municipio == null || municipio.isBlank()) {
            if (nearestRain != null && nearestRain.getMunicipality() != null) {
                municipio = nearestRain.getMunicipality();
            } else if (nearestRiver != null && nearestRiver.getMunicipality() != null) {
                municipio = nearestRiver.getMunicipality();
            }
            log.info("Município inferido: {}", municipio);
        }

        // 4. Inferir Bacia Hidrográfica
        String bacia = null;
        if (nearestRiver != null && nearestRiver.getBasinName() != null) {
            bacia = nearestRiver.getBasinName();
            log.info("Bacia hidrográfica identificada: {}", bacia);
        }

        FloodPoint floodPoint = FloodPoint.builder()
                .slug(request.getId_ponto())
                .name(request.getNome())
                .municipality(municipio)
                .description(request.getDescricao())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .altitudeM(altitude)
                .distanceToChannelM(request.getDist_canal_m())
                .pluviometerStationId(pluviometerId)
                .riverLevelStationId(riverLevelId)
                .basinName(bacia)
                .active(true)
                .build();
        
        floodPoint = floodPointRepository.save(floodPoint);
        FloodPointResponseDTO response = convertToResponseDTO(floodPoint);
        
        // Garantir que a maré seja incluída na resposta da criação
        try {
            response.setTideHeight(tideService.getCurrentTideHeight(request.getLatitude(), request.getLongitude()));
            response.setTideUnit("m");
        } catch (Exception e) {
            log.warn("Erro ao buscar maré inicial para novo ponto: {}", e.getMessage());
        }
        
        return response;
    }

    private SensorResponseDTO findNearestSensorByType(double lat, double lon, String type) {
        List<SensorResponseDTO> sensors = sensorService.getAllLatestData();
        return sensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .filter(s -> {
                    double dist = calculateDistance(lat, lon, s.getLatitude(), s.getLongitude());
                    if (dist > MAX_SENSOR_RADIUS_KM) return false;

                    if ("PRECIPITATION".equals(type)) {
                        return s.getAccumulatedPrecipitation() != null || "mm".equals(s.getUnit());
                    } else if ("RIVER_LEVEL".equals(type)) {
                        return s.getWaterLevel() != null || "m".equals(s.getUnit());
                    }
                    return false;
                })
                .min(Comparator.comparingDouble(s -> calculateDistance(lat, lon, s.getLatitude(), s.getLongitude())))
                .orElse(null);
    }

    @Override
    public List<FloodPointResponseDTO> getAllFloodPoints() {
        return floodPointRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .toList();
    }

    @Override
    public FloodPointResponseDTO getFloodPointBySlug(String slug) {
        return floodPointRepository.findBySlug(slug)
                .map(this::convertToResponseDTO)
                .orElse(null);
    }

    private FloodPointResponseDTO convertToResponseDTO(FloodPoint fp) {
        Double currentTide = null;
        try {
            currentTide = tideService.getCurrentTideHeight(fp.getLatitude(), fp.getLongitude());
        } catch (Exception e) {
            log.warn("Erro ao buscar maré para o ponto {}: {}", fp.getName(), e.getMessage());
        }

        return FloodPointResponseDTO.builder()
                .id(fp.getId())
                .id_ponto(fp.getSlug())
                .nome(fp.getName())
                .municipio(fp.getMunicipality())
                .descricao(fp.getDescription())
                .latitude(fp.getLatitude())
                .longitude(fp.getLongitude())
                .altitude_m(fp.getAltitudeM())
                .dist_canal_m(fp.getDistanceToChannelM())
                .bacia_hidrografica(fp.getBasinName())
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                        .estacao_pluviometrica_id(fp.getPluviometerStationId())
                        .estacao_nivel_rio_id(fp.getRiverLevelStationId())
                        .build())
                .active(fp.getActive())
                .tideHeight(currentTide)
                .tideUnit("m")
                .build();
    }

    private SensorResponseDTO findNearestSensor(double lat, double lon, List<SensorResponseDTO> sensors) {
        return sensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .min(Comparator.comparingDouble(s -> calculateDistance(lat, lon, s.getLatitude(), s.getLongitude())))
                .orElse(null);
    }

    private MapiResponseDTO.PreciseData determinePreciseData(WeatherResponseDTO weather, SensorResponseDTO sensor, Double distance, Double tideHeight, Double tideTabuaMare, Double waveHeight, Double waveDirection, Double wavePeriod) {
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
        builder.unitTide("m");
        builder.unitWave("m");

        // Adicionar Maré (Prioridade Marinha)
        if (tideHeight != null) {
            builder.tideHeight(tideHeight);
        } else if (tideTabuaMare != null) {
            builder.tideHeight(tideTabuaMare);
            builder.message("Dados de maré obtidos via TabuaMare (Fonte alternativa).");
        }

        builder.tideHeightTabuaMare(tideTabuaMare);
        builder.waveHeight(waveHeight);
        builder.waveDirection(waveDirection);
        builder.wavePeriod(wavePeriod);

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

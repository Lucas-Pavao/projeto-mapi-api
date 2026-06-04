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
import com.projeto.mapi.util.GeoUtils;
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
    private final com.projeto.mapi.service.FloodPredictionService floodPredictionService;
    private final com.projeto.mapi.service.DataExportService dataExportService;

    private static final double MAX_SENSOR_RADIUS_KM = 20.0;

    @Override
    public MapiResponseDTO getPreciseData(double latitude, double longitude) {
        log.info("Buscando dados precisos para lat: {}, lon: {}", latitude, longitude);
        
        WeatherResponseDTO weatherData = weatherService.getWeatherData(latitude, longitude);
        List<SensorResponseDTO> allSensors = sensorService.getAllLatestData();
        
        // --- NOVA LÓGICA: Filtrar sensores num raio de 5km ---
        List<SensorResponseDTO> nearbySensors = allSensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .filter(s -> GeoUtils.calculateDistance(latitude, longitude, s.getLatitude(), s.getLongitude()) <= 5.0)
                .toList();

        Double tideHeight = tideService.getCurrentTideHeight(latitude, longitude);
        Double tideTabuaMare = tabuaMareService.getCurrentTideHeight(latitude, longitude);
        Double waveHeight = marineService.getCurrentWaveHeight(latitude, longitude);
        Double waveDirection = marineService.getCurrentWaveDirection(latitude, longitude);
        Double wavePeriod = marineService.getCurrentWavePeriod(latitude, longitude);

        SensorResponseDTO nearestSensor = findNearestSensor(latitude, longitude, allSensors);
        Double distance = null;
        if (nearestSensor != null) {
            distance = GeoUtils.calculateDistance(latitude, longitude, nearestSensor.getLatitude(), nearestSensor.getLongitude());
            log.info("Sensor mais próximo encontrado: {} a {} km", nearestSensor.getSensorId(), String.format("%.2f", distance));
        }

        MapiResponseDTO.PreciseData preciseData = determinePreciseData(weatherData, nearbySensors, latitude, longitude, tideHeight, tideTabuaMare, waveHeight, waveDirection, wavePeriod);

        // --- Integração com IA em Tempo Real ---
        com.projeto.mapi.dto.FloodPredictionResponseDTO prediction = null;
        try {
            // Obter acumulados reais para o ponto (Regional - Raio 5km)
            Double acc3h = 0.0, acc6h = 0.0, acc12h = 0.0, acc24h = 0.0;
            
            // Busca o ponto mais próximo cadastrado para obter o slug e histórico
            FloodPoint nearestPoint = floodPointRepository.findAll().stream()
                .min(Comparator.comparingDouble(p -> GeoUtils.calculateDistance(latitude, longitude, p.getLatitude(), p.getLongitude())))
                .orElse(null);

            if (nearestPoint != null) {
                // Tenta buscar dados unificados recentes (últimas 24h) para calcular acumulados regionais
                List<com.projeto.mapi.dto.UnifiedDataDTO> history = dataExportService.exportUnifiedDataWithAccumulated(nearestPoint.getSlug(), 1);
                
                if (!history.isEmpty()) {
                    com.projeto.mapi.dto.UnifiedDataDTO latest = history.get(history.size() - 1);
                    acc3h = latest.getAccumulated3h() != null ? latest.getAccumulated3h() : 0.0;
                    acc6h = latest.getAccumulated6h() != null ? latest.getAccumulated6h() : 0.0;
                    acc12h = latest.getAccumulated12h() != null ? latest.getAccumulated12h() : 0.0;
                    acc24h = latest.getAccumulated24h() != null ? latest.getAccumulated24h() : 0.0;
                }
            }

            com.projeto.mapi.dto.FloodPredictionRequestDTO predictionRequest = com.projeto.mapi.dto.FloodPredictionRequestDTO.builder()
                    .stationId(nearestSensor != null ? nearestSensor.getSensorId() : "VIRTUAL_STATION")
                    .latitude(latitude)
                    .longitude(longitude)
                    .currentRainfall(preciseData.getPrecipitation())
                    .rainfall3hAccumulated(acc3h)
                    .rainfall6hAccumulated(acc6h)
                    .rainfall12hAccumulated(acc12h)
                    .rainfall24hAccumulated(acc24h)
                    .tideLevel(preciseData.getTideHeight())
                    .riverLevel(preciseData.getWaterLevel())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            prediction = floodPredictionService.getPrediction(predictionRequest);
        } catch (Exception e) {
            log.error("Falha ao obter predição da IA: {}", e.getMessage());
        }

        return MapiResponseDTO.builder()
                .requestedLatitude(latitude)
                .requestedLongitude(longitude)
                .preciseData(preciseData)
                .nearestSensor(nearestSensor)
                .openMeteoData(weatherData)
                .distanceToNearestSensorKm(distance)
                .floodPrediction(prediction)
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
                    double dist = GeoUtils.calculateDistance(lat, lon, s.getLatitude(), s.getLongitude());
                    if (dist > MAX_SENSOR_RADIUS_KM) return false;

                    if ("PRECIPITATION".equals(type)) {
                        return s.getAccumulatedPrecipitation() != null || "mm".equals(s.getUnit());
                    } else if ("RIVER_LEVEL".equals(type)) {
                        return s.getWaterLevel() != null || "m".equals(s.getUnit());
                    }
                    return false;
                })
                .min(Comparator.comparingDouble(s -> GeoUtils.calculateDistance(lat, lon, s.getLatitude(), s.getLongitude())))
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
                .min(Comparator.comparingDouble(s -> GeoUtils.calculateDistance(lat, lon, s.getLatitude(), s.getLongitude())))
                .orElse(null);
    }

    private MapiResponseDTO.PreciseData determinePreciseData(WeatherResponseDTO weather, List<SensorResponseDTO> nearbySensors, Double latitude, Double longitude, Double tideHeight, Double tideTabuaMare, Double waveHeight, Double waveDirection, Double wavePeriod) {
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

        // --- LÓGICA REGIONAL: Agregar dados de sensores num raio de 5km ---
        if (nearbySensors != null && !nearbySensors.isEmpty()) {
            builder.source("MIXED (Regional Aggregation)");
            builder.message("Dados otimizados: Agregando " + nearbySensors.size() + " sensores num raio de 5km.");
            
            // Pega o MÁXIMO de precipitação da região por segurança
            double maxRain = nearbySensors.stream()
                .mapToDouble(s -> s.getAccumulatedPrecipitation() != null ? s.getAccumulatedPrecipitation() : 0.0)
                .max().orElse(0.0);
            
            // Pega o MÁXIMO de nível de rio da região
            double maxRiver = nearbySensors.stream()
                .mapToDouble(s -> s.getWaterLevel() != null ? s.getWaterLevel() : 0.0)
                .max().orElse(0.0);

            builder.precipitation(maxRain);
            builder.waterLevel(maxRiver);

            // Outras métricas (Média)
            nearbySensors.stream()
                .filter(s -> s.getTemperature() != null)
                .mapToDouble(SensorResponseDTO::getTemperature)
                .average().ifPresent(builder::temperature);

            nearbySensors.stream()
                .filter(s -> s.getHumidity() != null)
                .mapToDouble(SensorResponseDTO::getHumidity)
                .average().ifPresent(builder::humidity);
            
            // Usar o timestamp do sensor mais recente
            nearbySensors.stream()
                .map(SensorResponseDTO::getTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .ifPresent(builder::timestamp);
        } else {
            builder.message("Dados baseados em Open-Meteo. Nenhum sensor regional encontrado num raio de 5km.");
        }

        return builder.build();
    }

    @Override
    public void seedPilotData() {
        if (floodPointRepository.count() > 0) return;
        
        log.info("Semeando pontos piloto de monitoramento...");
        List<FloodPointRequestDTO> pilots = List.of(
            FloodPointRequestDTO.builder()
                .id_ponto("AV_RECIFE_IBURA")
                .nome("Av. Recife - Entrada do Ibura")
                .latitude(-8.107910)
                .longitude(-34.927138)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacao_pluviometrica_id("APAC-PLUVIO-261160615A")
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("CIN_UFPE")
                .nome("CIn - UFPE")
                .latitude(-8.055310)
                .longitude(-34.951160)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacao_pluviometrica_id("APAC-PLUVIO-261160601A")
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("AGAMENON_DERBY")
                .nome("Av. Agamenon Magalhães (Derby)")
                .latitude(-8.052554)
                .longitude(-34.894371)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacao_pluviometrica_id("APAC-PLUVIO-261160621A")
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("JABOATAO_CENTRO")
                .nome("Jaboatão Centro (Rio Duas Unas)")
                .latitude(-8.106520)
                .longitude(-35.013210)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacao_pluviometrica_id("APAC-METEO-260790119H")
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("MASCARENHAS_IMBIRIBEIRA")
                .nome("Av. Mascarenhas de Morais")
                .latitude(-8.118123)
                .longitude(-34.904945)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacao_pluviometrica_id("APAC-PLUVIO-261160609A")
                    .build())
                .build()
        );

        pilots.forEach(this::createFloodPoint);
        log.info("5 pontos piloto cadastrados com sucesso.");
    }
}

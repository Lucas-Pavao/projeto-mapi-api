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
import com.projeto.mapi.model.FloodPrediction;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.model.FloodScenarioLabel;
import com.projeto.mapi.repository.FloodScenarioLabelRepository;
import com.projeto.mapi.dto.FloodScenarioLabelRequestDTO;
import com.projeto.mapi.dto.FloodScenarioLabelResponseDTO;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.model.SensorData;

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
    private final FloodPredictionRepository floodPredictionRepository;
    private final FloodScenarioLabelRepository floodScenarioLabelRepository;
    private final SensorDataRepository sensorDataRepository;

    private static final double MAX_SENSOR_RADIUS_KM = 20.0;

    @Override
    public MapiResponseDTO getPreciseData(double latitude, double longitude) {
        log.info("Buscando dados precisos para lat: {}, lon: {}", latitude, longitude);
        
        WeatherResponseDTO weatherData = weatherService.getWeatherData(latitude, longitude);
        List<SensorResponseDTO> allSensors = sensorService.getAllLatestData();
        
        // --- NOVA LÓGICA: Filtrar sensores num raio de 3km ---
        List<SensorResponseDTO> nearbySensors = allSensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .filter(s -> GeoUtils.calculateDistance(latitude, longitude, s.getLatitude(), s.getLongitude()) <= 3.0)
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
            // Obter acumulados reais para o ponto (Regional - Raio 3km)
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
                    .nearbySensors(preciseData.getLatestReadings())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            prediction = floodPredictionService.getPrediction(predictionRequest);

            // Persistir a predição no banco de dados para auditoria / histórico
            try {
                FloodPrediction loggedPrediction = FloodPrediction.builder()
                        .timestamp(predictionRequest.getTimestamp() != null ? predictionRequest.getTimestamp() : LocalDateTime.now())
                        .stationId(predictionRequest.getStationId())
                        .latitude(predictionRequest.getLatitude())
                        .longitude(predictionRequest.getLongitude())
                        .currentRainfall(predictionRequest.getCurrentRainfall())
                        .rainfall3hAccumulated(predictionRequest.getRainfall3hAccumulated())
                        .rainfall6hAccumulated(predictionRequest.getRainfall6hAccumulated())
                        .rainfall12hAccumulated(predictionRequest.getRainfall12hAccumulated())
                        .rainfall24hAccumulated(predictionRequest.getRainfall24hAccumulated())
                        .tideLevel(predictionRequest.getTideLevel())
                        .riverLevel(predictionRequest.getRiverLevel())
                        .floodProbability(prediction != null ? prediction.getFloodProbability() : 0.0)
                        .riskLevel(prediction != null ? prediction.getRiskLevel() : "UNKNOWN")
                        .status(prediction != null && !"UNKNOWN".equals(prediction.getRiskLevel()) ? "SUCCESS" : "FAILED")
                        .message(prediction != null ? prediction.getMessage() : "Sem resposta ou erro do modelo de IA")
                        .build();
                floodPredictionRepository.save(loggedPrediction);
            } catch (Exception ex) {
                log.error("Erro ao salvar histórico de predição no banco: {}", ex.getMessage());
            }
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
    @org.springframework.cache.annotation.CacheEvict(value = "floodPoints", allEntries = true)
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

        // 2. Mapeamento de Sensores Regionais (Raio 3km)
        List<SensorResponseDTO> allSensors = sensorService.getAllLatestData();
        
        java.util.Set<String> pluviometerIds = new java.util.HashSet<>();
        if (request.getConfig_sensores() != null && request.getConfig_sensores().getEstacoes_pluviometricas_ids() != null) {
            pluviometerIds.addAll(request.getConfig_sensores().getEstacoes_pluviometricas_ids());
        }

        java.util.Set<String> riverLevelIds = new java.util.HashSet<>();
        if (request.getConfig_sensores() != null && request.getConfig_sensores().getEstacoes_nivel_rio_ids() != null) {
            riverLevelIds.addAll(request.getConfig_sensores().getEstacoes_nivel_rio_ids());
        }

        // Auto-vincular TODOS os sensores num raio de 3km
        allSensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .filter(s -> GeoUtils.calculateDistance(request.getLatitude(), request.getLongitude(), s.getLatitude(), s.getLongitude()) <= 3.0)
                .forEach(s -> {
                    if (s.getAccumulatedPrecipitation() != null || "mm".equals(s.getUnit())) {
                        pluviometerIds.add(s.getSensorId());
                    }
                    if (s.getWaterLevel() != null || "m".equals(s.getUnit())) {
                        riverLevelIds.add(s.getSensorId());
                    }
                });

        // 3. Inferir Município se não fornecido
        String municipio = request.getMunicipio();
        if (municipio == null || municipio.isBlank()) {
            SensorResponseDTO nearest = findNearestSensor(request.getLatitude(), request.getLongitude(), allSensors);
            if (nearest != null && nearest.getMunicipality() != null) {
                municipio = nearest.getMunicipality();
            }
            log.info("Município inferido: {}", municipio);
        }

        // 4. Inferir Bacia Hidrográfica
        String bacia = null;
        SensorResponseDTO nearestRiver = findNearestSensorByType(request.getLatitude(), request.getLongitude(), "RIVER_LEVEL");
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
                .pluviometerStationIds(pluviometerIds)
                .riverLevelStationIds(riverLevelIds)
                .basinName(bacia)
                .active(true)
                .build();
        
        floodPoint = floodPointRepository.save(floodPoint);
        List<SensorResponseDTO> allSensorsList = sensorService.getAllLatestData();
        FloodPointResponseDTO response = convertToResponseDTO(floodPoint, allSensorsList);
        
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
    @Transactional
    public List<FloodPointResponseDTO> getAllFloodPoints() {
        List<SensorResponseDTO> allSensors = sensorService.getAllLatestData();
        return floodPointRepository.findAll().stream()
                .map(fp -> {
                    syncSensors(fp, allSensors);
                    return convertToResponseDTO(fp, allSensors);
                })
                .toList();
    }

    @Override
    @Transactional
    public FloodPointResponseDTO getFloodPointBySlug(String slug) {
        List<SensorResponseDTO> allSensors = sensorService.getAllLatestData();
        return floodPointRepository.findBySlug(slug)
                .map(fp -> {
                    syncSensors(fp, allSensors);
                    return convertToResponseDTO(fp, allSensors, true);
                })
                .orElse(null);
    }

    private void syncSensors(FloodPoint fp, List<SensorResponseDTO> allSensors) {
        boolean updated = false;
        
        // 1. Sincronizar Sensores num raio de 3km
        List<SensorResponseDTO> nearby = allSensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .filter(s -> GeoUtils.calculateDistance(fp.getLatitude(), fp.getLongitude(), s.getLatitude(), s.getLongitude()) <= 3.0)
                .toList();

        for (SensorResponseDTO s : nearby) {
            if ((s.getAccumulatedPrecipitation() != null || "mm".equals(s.getUnit())) 
                && !fp.getPluviometerStationIds().contains(s.getSensorId())) {
                fp.getPluviometerStationIds().add(s.getSensorId());
                updated = true;
            }
            if ((s.getWaterLevel() != null || "m".equals(s.getUnit())) 
                && !fp.getRiverLevelStationIds().contains(s.getSensorId())) {
                fp.getRiverLevelStationIds().add(s.getSensorId());
                updated = true;
            }
        }

        // 2. Reparar Município se nulo
        if (fp.getMunicipality() == null || fp.getMunicipality().isBlank()) {
            SensorResponseDTO nearest = nearby.stream()
                    .filter(s -> s.getMunicipality() != null)
                    .min(Comparator.comparingDouble(s -> GeoUtils.calculateDistance(fp.getLatitude(), fp.getLongitude(), s.getLatitude(), s.getLongitude())))
                    .orElse(null);
            
            if (nearest != null) {
                fp.setMunicipality(nearest.getMunicipality());
                updated = true;
                log.info("Município do ponto {} reparado automaticamente para: {}", fp.getSlug(), fp.getMunicipality());
            }
        }

        if (updated) {
            floodPointRepository.save(fp);
        }
    }

    private FloodPointResponseDTO convertToResponseDTO(FloodPoint fp, List<SensorResponseDTO> allSensors) {
        return convertToResponseDTO(fp, allSensors, false);
    }

    private FloodPointResponseDTO convertToResponseDTO(FloodPoint fp, List<SensorResponseDTO> allSensors, boolean includeLiveData) {
        Double currentTide = null;
        try {
            currentTide = tideService.getCurrentTideHeight(fp.getLatitude(), fp.getLongitude());
        } catch (Exception e) {
            log.warn("Erro ao buscar maré para o ponto {}: {}", fp.getName(), e.getMessage());
        }

        List<String> nearbySensorIds = allSensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .filter(s -> GeoUtils.calculateDistance(fp.getLatitude(), fp.getLongitude(), s.getLatitude(), s.getLongitude()) <= 3.0)
                .map(SensorResponseDTO::getSensorId)
                .toList();

        FloodPointResponseDTO.FloodPointResponseDTOBuilder builder = FloodPointResponseDTO.builder()
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
                .sensores_proximos_ids(nearbySensorIds)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                        .estacoes_pluviometricas_ids(new java.util.ArrayList<>(fp.getPluviometerStationIds()))
                        .estacoes_nivel_rio_ids(new java.util.ArrayList<>(fp.getRiverLevelStationIds()))
                        .build())
                .active(fp.getActive())
                .tideHeight(currentTide)
                .tideUnit("m");

        if (includeLiveData) {
            MapiResponseDTO liveData = getPreciseData(fp.getLatitude(), fp.getLongitude());
            builder.liveData(liveData.getPreciseData());
            builder.floodPrediction(liveData.getFloodPrediction());
        }

        return builder.build();
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
            builder.pressure(weather.current().surfacePressure());
            builder.windSpeed(weather.current().windSpeed());
            builder.solarRadiation(weather.current().solarRadiation());
            
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
        builder.unitPressure("hPa");
        builder.unitWindSpeed("km/h");
        builder.unitSolarRadiation("W/m²");
        builder.unitFlowRate("m³/s");

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

        // --- LÓGICA REGIONAL: Agregar dados de sensores num raio de 3km ---
        if (nearbySensors != null && !nearbySensors.isEmpty()) {
            builder.source("MIXED (Regional Aggregation)");
            builder.message("Dados otimizados: Agregando " + nearbySensors.size() + " sensores num raio de 3km.");
            builder.sensorIds(nearbySensors.stream().map(SensorResponseDTO::getSensorId).toList());
            
            // 1. Mapear LEITURAS RECENTES (Latest)
            List<MapiResponseDTO.SensorReadingDTO> latest = nearbySensors.stream()
                .map(s -> MapiResponseDTO.SensorReadingDTO.builder()
                    .sensorId(s.getSensorId())
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
                    .value(s.getAccumulatedPrecipitation() != null ? s.getAccumulatedPrecipitation() : s.getWaterLevel())
                    .unit(s.getUnit())
                    .type(s.getAccumulatedPrecipitation() != null ? "PRECIPITATION" : "RIVER_LEVEL")
                    .timestamp(s.getTimestamp())
                    .distanceKm(GeoUtils.calculateDistance(latitude, longitude, s.getLatitude(), s.getLongitude()))
                    .build())
                .toList();
            builder.latestReadings(latest);
            
            // 2. Calcular ACUMULADOS (Aggregates) via DataExportService
            // Busca o ponto mais próximo cadastrado para obter o contexto regional
            FloodPoint nearestPoint = floodPointRepository.findAll().stream()
                .min(Comparator.comparingDouble(p -> GeoUtils.calculateDistance(latitude, longitude, p.getLatitude(), p.getLongitude())))
                .orElse(null);

            if (nearestPoint != null) {
                try {
                    List<com.projeto.mapi.dto.UnifiedDataDTO> history = dataExportService.exportUnifiedDataWithAccumulated(nearestPoint.getSlug(), 1);
                    if (!history.isEmpty()) {
                        com.projeto.mapi.dto.UnifiedDataDTO last = history.get(history.size() - 1);
                        builder.historicalAggregates(MapiResponseDTO.Aggregates.builder()
                                .rain3h(last.getAccumulated3h())
                                .rain6h(last.getAccumulated6h())
                                .rain12h(last.getAccumulated12h())
                                .rain24h(last.getAccumulated24h())
                                .maxRiverLevel24h(last.getSensorWaterLevel()) // Simplificado para o nível regional atual
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("Falha ao calcular agregados históricos regionais: {}", e.getMessage());
                }
            }

            // Pega o MÁXIMO de precipitação da região por segurança
            double maxRain = nearbySensors.stream()
                .mapToDouble(s -> s.getAccumulatedPrecipitation() != null ? s.getAccumulatedPrecipitation() : 0.0)
                .max().orElse(0.0);
            
            // Pega o MÁXIMO de nível de rio da região
            double maxRiver = nearbySensors.stream()
                .mapToDouble(s -> s.getWaterLevel() != null ? s.getWaterLevel() : 0.0)
                .max().orElse(0.0);
            
            // Pega o MÁXIMO de vazão da região
            double maxFlow = nearbySensors.stream()
                .mapToDouble(s -> s.getFlowRate() != null ? s.getFlowRate() : 0.0)
                .max().orElse(0.0);

            builder.precipitation(maxRain);
            builder.waterLevel(maxRiver);
            builder.flowRate(maxFlow);

            // Outras métricas (Média)
            nearbySensors.stream()
                .filter(s -> s.getTemperature() != null)
                .mapToDouble(SensorResponseDTO::getTemperature)
                .average().ifPresent(builder::temperature);

            nearbySensors.stream()
                .filter(s -> s.getHumidity() != null)
                .mapToDouble(SensorResponseDTO::getHumidity)
                .average().ifPresent(builder::humidity);

            nearbySensors.stream()
                .filter(s -> s.getPressure() != null)
                .mapToDouble(SensorResponseDTO::getPressure)
                .average().ifPresent(builder::pressure);

            nearbySensors.stream()
                .filter(s -> s.getWindSpeed() != null)
                .mapToDouble(SensorResponseDTO::getWindSpeed)
                .average().ifPresent(builder::windSpeed);

            nearbySensors.stream()
                .filter(s -> s.getSolarRadiation() != null)
                .mapToDouble(SensorResponseDTO::getSolarRadiation)
                .average().ifPresent(builder::solarRadiation);
            
            // Usar o timestamp do sensor mais recente
            nearbySensors.stream()
                .map(SensorResponseDTO::getTimestamp)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .ifPresent(builder::timestamp);
        } else {
            builder.message("Dados baseados em Open-Meteo. Nenhum sensor regional encontrado num raio de 3km.");
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
                    .estacoes_pluviometricas_ids(List.of("APAC-PLUVIO-261160615A"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("CIN_UFPE")
                .nome("CIn - UFPE")
                .latitude(-8.055310)
                .longitude(-34.951160)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacoes_pluviometricas_ids(List.of("APAC-PLUVIO-261160601A"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("AGAMENON_DERBY")
                .nome("Av. Agamenon Magalhães (Derby)")
                .latitude(-8.052554)
                .longitude(-34.894371)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacoes_pluviometricas_ids(List.of("APAC-PLUVIO-261160621A"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("JABOATAO_CENTRO")
                .nome("Jaboatão Centro (Rio Duas Unas)")
                .latitude(-8.106520)
                .longitude(-35.013210)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacoes_pluviometricas_ids(List.of("APAC-METEO-260790119H"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .id_ponto("MASCARENHAS_IMBIRIBEIRA")
                .nome("Av. Mascarenhas de Morais")
                .latitude(-8.118123)
                .longitude(-34.904945)
                .config_sensores(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .estacoes_pluviometricas_ids(List.of("APAC-PLUVIO-261160609A"))
                    .build())
                .build()
        );

        pilots.forEach(this::createFloodPoint);
        log.info("5 pontos piloto cadastrados com sucesso.");
    }

    @Override
    @Transactional
    public FloodScenarioLabelResponseDTO registerScenarioLabel(FloodScenarioLabelRequestDTO request) {
        double latitude = request.latitude();
        double longitude = request.longitude();
        
        log.info("Registrando cenário de alagamento: lat={}, lon={}, isFlooded={}", latitude, longitude, request.isFlooded());
        
        // 1. Obter dados de clima, sensores, maré e ondas
        WeatherResponseDTO weatherData = weatherService.getWeatherData(latitude, longitude);
        List<SensorResponseDTO> allSensors = sensorService.getAllLatestData();
        
        List<SensorResponseDTO> nearbySensors = allSensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .filter(s -> GeoUtils.calculateDistance(latitude, longitude, s.getLatitude(), s.getLongitude()) <= 3.0)
                .toList();

        Double tideHeight = tideService.getCurrentTideHeight(latitude, longitude);
        Double tideTabuaMare = tabuaMareService.getCurrentTideHeight(latitude, longitude);
        Double waveHeight = marineService.getCurrentWaveHeight(latitude, longitude);
        Double waveDirection = marineService.getCurrentWaveDirection(latitude, longitude);
        Double wavePeriod = marineService.getCurrentWavePeriod(latitude, longitude);

        MapiResponseDTO.PreciseData preciseData = determinePreciseData(weatherData, nearbySensors, latitude, longitude, tideHeight, tideTabuaMare, waveHeight, waveDirection, wavePeriod);

        // 2. Calcular acumulados de chuva regionais diretamente dos sensores em raio de 3km
        Double acc3h = 0.0, acc6h = 0.0, acc12h = 0.0, acc24h = 0.0;
        try {
            acc3h = calculateSensorAccumulatedRainfall(latitude, longitude, 3);
            acc6h = calculateSensorAccumulatedRainfall(latitude, longitude, 6);
            acc12h = calculateSensorAccumulatedRainfall(latitude, longitude, 12);
            acc24h = calculateSensorAccumulatedRainfall(latitude, longitude, 24);
        } catch (Exception e) {
            log.warn("Erro ao calcular acumulados regionais para registro de rótulo: {}", e.getMessage());
        }

        // 3. Montar e persistir o objeto FloodScenarioLabel
        LocalDateTime now = LocalDateTime.now();
        
        Double currentRain = preciseData.getPrecipitation();
        Double actualTide = preciseData.getTideHeight();
        Double actualRiver = preciseData.getWaterLevel();
        
        Double windSpeed = null;
        String windDirection = null;
        Double temp = null;
        Double apparentTemp = null;
        Double humidity = null;
        Double pressure = null;
        Double solarRad = null;
        
        if (weatherData != null && weatherData.current() != null) {
            windSpeed = weatherData.current().windSpeed();
            temp = weatherData.current().temperature();
            apparentTemp = weatherData.current().apparentTemperature();
            humidity = (double) weatherData.current().humidity();
            pressure = weatherData.current().surfacePressure();
            solarRad = weatherData.current().solarRadiation();
        }

        FloodScenarioLabel label = FloodScenarioLabel.builder()
                .timestamp(now)
                .latitude(latitude)
                .longitude(longitude)
                .isFlooded(request.isFlooded())
                .currentRainfall(currentRain)
                .rainfall3hAccumulated(acc3h)
                .rainfall6hAccumulated(acc6h)
                .rainfall12hAccumulated(acc12h)
                .rainfall24hAccumulated(acc24h)
                .tideLevel(actualTide)
                .riverLevel(actualRiver)
                .windSpeed(windSpeed)
                .windDirection(windDirection)
                .temperature(temp)
                .apparentTemperature(apparentTemp)
                .humidity(humidity)
                .pressure(pressure)
                .waveHeight(waveHeight)
                .wavePeriod(wavePeriod)
                .waveDirection(waveDirection)
                .solarRadiation(solarRad)
                .build();

        label = floodScenarioLabelRepository.save(label);

        return new FloodScenarioLabelResponseDTO(
                label.getId(),
                label.getTimestamp(),
                label.getLatitude(),
                label.getLongitude(),
                label.getIsFlooded(),
                label.getCurrentRainfall(),
                label.getRainfall3hAccumulated(),
                label.getRainfall6hAccumulated(),
                label.getRainfall12hAccumulated(),
                label.getRainfall24hAccumulated(),
                label.getTideLevel(),
                label.getRiverLevel(),
                label.getWindSpeed(),
                label.getWindDirection(),
                label.getTemperature(),
                label.getApparentTemperature(),
                label.getHumidity(),
                label.getPressure(),
                label.getWaveHeight(),
                label.getWavePeriod(),
                label.getWaveDirection(),
                label.getSolarRadiation()
        );
    }

    private Double calculateSensorAccumulatedRainfall(double lat, double lon, int hours) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(hours);
        
        // Buscar todos os dados de sensores na região de 3km no período
        List<SensorData> sensorData = sensorDataRepository.findSensorsByRadius(lat, lon, 3.0, start, now);
        
        // Agrupar por hora (Truncar minutos/segundos) e pegar o valor máximo de precipitação naquela hora
        java.util.Map<LocalDateTime, Double> hourlyMaxPrecip = new java.util.HashMap<>();
        for (SensorData s : sensorData) {
            if (s.getAccumulatedPrecipitation() != null) {
                LocalDateTime hour = s.getTimestamp().withMinute(0).withSecond(0).withNano(0);
                double val = s.getAccumulatedPrecipitation();
                hourlyMaxPrecip.put(hour, Math.max(hourlyMaxPrecip.getOrDefault(hour, 0.0), val));
            }
        }
        
        // Somar os máximos de cada hora no período
        double sum = hourlyMaxPrecip.values().stream().mapToDouble(Double::doubleValue).sum();
        return Math.round(sum * 100.0) / 100.0;
    }
}

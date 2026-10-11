package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.FloodPointRequestDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.MapiResponseDTO;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.MapiService;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.tide.TideService;
import com.projeto.mapi.service.weather.WeatherService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
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
    private final com.projeto.mapi.service.tide.TabuaMareService tabuaMareService;
    private final com.projeto.mapi.service.weather.MarineService marineService;
    private final FloodPointRepository floodPointRepository;
    private final com.projeto.mapi.service.flood.FloodPredictionService floodPredictionService;
    private final com.projeto.mapi.service.export.DataExportService dataExportService;
    private final com.projeto.mapi.service.flood.FloodPredictionHistoryService predictionHistory;
    private final FloodScenarioLabelRepository floodScenarioLabelRepository;
    private final SensorDataRepository sensorDataRepository;

    private static final double MAX_SENSOR_RADIUS_KM = 20.0;

    @Override
    public MapiResponseDTO getEnvironmentalData(double latitude, double longitude) {
        log.info("Buscando dado ambiental para lat: {}, lon: {}", latitude, longitude);

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

        // Busca o ponto crítico mais próximo e seu histórico uma única vez, e reaproveita
        // tanto na agregação regional quanto na montagem do payload de predição da IA
        // (antes esse mesmo cálculo era refeito duas vezes por requisição).
        FloodPoint nearestPoint = floodPointRepository.findAll().stream()
            .min(Comparator.comparingDouble(p -> GeoUtils.calculateDistance(latitude, longitude, p.getLatitude(), p.getLongitude())))
            .orElse(null);
        List<com.projeto.mapi.dto.UnifiedDataDTO> nearestPointHistory = nearestPoint != null
            ? dataExportService.exportUnifiedDataWithAccumulated(nearestPoint.getSlug(), 1)
            : List.of();

        MapiResponseDTO.PreciseData preciseData = determinePreciseData(weatherData, nearbySensors, latitude, longitude, tideHeight, tideTabuaMare, waveHeight, waveDirection, wavePeriod, nearestPointHistory);

        // floodPrediction fica deliberadamente de fora daqui: este método é só o dado ambiental
        // (sensores + clima + maré + ondas), sem chamar a MAPI AI nem gravar auditoria de
        // predição. Pensado para ser chamado com frequência (ex: mapa em tempo real) sem
        // custo/risco de uma IA externa.
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
    public com.projeto.mapi.dto.FloodPredictionResponseDTO createFloodPrediction(double latitude, double longitude) {
        MapiResponseDTO environmentalData = getEnvironmentalData(latitude, longitude);
        return predictAndStore(null, environmentalData.getPreciseData(), environmentalData.getNearestSensor(), latitude, longitude);
    }

    // Leitura pura (ADR 0007 §7): dado ambiental + ÚLTIMA previsão gravada do ponto. Não chama a IA
    // nem grava em flood_predictions; quem prevê é o passo agendado ao fim do ciclo de coleta
    // (refreshPredictionsForActivePoints).
    @Override
    public MapiResponseDTO getPreciseData(double latitude, double longitude) {
        return getPreciseData(null, latitude, longitude);
    }

    private MapiResponseDTO getPreciseData(Long floodPointId, double latitude, double longitude) {
        MapiResponseDTO environmentalData = getEnvironmentalData(latitude, longitude);
        environmentalData.setFloodPrediction(predictionHistory.findLatest(floodPointId, latitude, longitude).orElse(null));
        return environmentalData;
    }

    // Passo de predição do ciclo de coleta: uma previsão por ponto ativo, gravada em flood_predictions.
    // Falha de um ponto não impede os demais. Chamado em thread do taskExecutor (PredictionCycleRunner).
    @Override
    public int refreshPredictionsForActivePoints() {
        int done = 0;
        for (FloodPoint point : floodPointRepository.findByActiveTrue()) {
            try {
                MapiResponseDTO env = getEnvironmentalData(point.getLatitude(), point.getLongitude());
                predictAndStore(point.getId(), env.getPreciseData(), env.getNearestSensor(), point.getLatitude(), point.getLongitude());
                done++;
            } catch (Exception e) {
                log.error("Falha ao atualizar a previsão do ponto {}: {}", point.getSlug(), e.getMessage());
            }
        }
        log.info("Previsões atualizadas para {} ponto(s) ativo(s)", done);
        return done;
    }

    // Chama a MAPI AI com o contexto ambiental já calculado e audita a previsão em
    // flood_predictions. Único ponto de escrita de previsões: usado pelo POST /api/flood-predictions
    // e pelo passo agendado do ciclo de coleta.
    private com.projeto.mapi.dto.FloodPredictionResponseDTO predictAndStore(
            Long floodPointId, MapiResponseDTO.PreciseData preciseData, SensorResponseDTO nearestSensor, double latitude, double longitude) {
        try {
            // Acumulados reais do ponto (regional, raio de 3 km), calculados em determinePreciseData.
            // Onde falta dado o payload leva 0.0; cada 0.0 imputado é DECLARADO em imputedFeatures
            // (ADR 0007 §5). O valor enviado à IA não muda.
            MapiResponseDTO.Aggregates aggregates = preciseData.getHistoricalAggregates();
            List<String> imputed = new java.util.ArrayList<>();
            Double acc3h = orImpute(aggregates != null ? aggregates.getRain3h() : null, "rainfall_3h_accumulated", imputed);
            Double acc6h = orImpute(aggregates != null ? aggregates.getRain6h() : null, "rainfall_6h_accumulated", imputed);
            Double acc12h = orImpute(aggregates != null ? aggregates.getRain12h() : null, "rainfall_12h_accumulated", imputed);
            Double acc24h = orImpute(aggregates != null ? aggregates.getRain24h() : null, "rainfall_24h_accumulated", imputed);
            Double tide = orImpute(preciseData.getTideHeight(), "tide_level", imputed);
            Double river = orImpute(preciseData.getWaterLevel(), "river_level", imputed);

            com.projeto.mapi.dto.FloodPredictionRequestDTO predictionRequest = com.projeto.mapi.dto.FloodPredictionRequestDTO.builder()
                    .stationId(nearestSensor != null ? nearestSensor.getSensorId() : "VIRTUAL_STATION")
                    .latitude(latitude)
                    .longitude(longitude)
                    .currentRainfall(preciseData.getPrecipitation())
                    .rainfall3hAccumulated(acc3h)
                    .rainfall6hAccumulated(acc6h)
                    .rainfall12hAccumulated(acc12h)
                    .rainfall24hAccumulated(acc24h)
                    .tideLevel(tide)
                    .riverLevel(river)
                    .windSpeed(preciseData.getWindSpeed())
                    .temperature(preciseData.getTemperature())
                    .apparentTemperature(preciseData.getApparentTemperature())
                    .humidity(preciseData.getHumidity())
                    .pressure(preciseData.getPressure())
                    .waveHeight(preciseData.getWaveHeight())
                    .wavePeriod(preciseData.getWavePeriod())
                    .waveDirection(preciseData.getWaveDirection())
                    .solarRadiation(preciseData.getSolarRadiation())
                    .nearbySensors(toAiSensorReadings(preciseData.getLatestReadings()))
                    .timestamp(LocalDateTime.now())
                    .build();

            com.projeto.mapi.service.flood.PredictionResult result = floodPredictionService.predict(predictionRequest, imputed);
            predictionHistory.record(predictionRequest, result, floodPointId);

            com.projeto.mapi.dto.FloodPredictionResponseDTO prediction = result.response();
            if (prediction.lastValidIssuedAt() == null) {
                prediction = prediction.toBuilder()
                        .lastValidIssuedAt(predictionHistory.findLastValidIssuedAt(floodPointId, latitude, longitude).orElse(null))
                        .build();
            }
            return prediction;
        } catch (Exception e) {
            log.error("Falha ao obter predição da IA: {}", e.getMessage());
            com.projeto.mapi.dto.DegradationReason reason = com.projeto.mapi.dto.DegradationReason.AI_ERROR;
            return com.projeto.mapi.dto.FloodPredictionResponseDTO.builder()
                    .riskLevel(com.projeto.mapi.dto.RiskLevel.UNKNOWN)
                    .issuedAt(java.time.Instant.now())
                    .degradationReason(reason)
                    .message(reason.message())
                    .build();
        }
    }

    private static Double orImpute(Double value, String featureId, List<String> imputed) {
        if (value != null) return value;
        imputed.add(featureId);
        return 0.0;
    }

    @Override
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "floodPoints", allEntries = true)
    public FloodPointResponseDTO createFloodPoint(FloodPointRequestDTO request) {
        log.info("Criando novo ponto de alagamento com hiper-automação: {}", request.getName());

        // 1. Obter Altitude automaticamente via Open-Meteo
        Double altitude = request.getAltitudeM();
        try {
            WeatherResponseDTO weather = weatherService.getWeatherData(request.getLatitude(), request.getLongitude());
            if (weather != null && altitude == null) {
                altitude = weather.elevation();
                log.info("Altitude obtida automaticamente: {}m", altitude);
            }
        } catch (Exception e) {
            log.warn("Não foi possível obter altitude automaticamente para o ponto {}", request.getName());
        }

        // 2. Mapeamento de Sensores Regionais (Raio 3km)
        List<SensorResponseDTO> allSensors = sensorService.getAllLatestData();

        java.util.Set<String> pluviometerIds = new java.util.HashSet<>();
        if (request.getSensorConfig() != null && request.getSensorConfig().getPluviometerStationIds() != null) {
            pluviometerIds.addAll(request.getSensorConfig().getPluviometerStationIds());
        }

        java.util.Set<String> riverLevelIds = new java.util.HashSet<>();
        if (request.getSensorConfig() != null && request.getSensorConfig().getRiverLevelStationIds() != null) {
            riverLevelIds.addAll(request.getSensorConfig().getRiverLevelStationIds());
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
        String municipio = request.getMunicipality();
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
                .slug(request.getSlug())
                .name(request.getName())
                .municipality(municipio)
                .description(request.getDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .altitudeM(altitude)
                .distanceToChannelM(request.getDistanceToChannelM())
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
                .slug(fp.getSlug())
                .name(fp.getName())
                .municipality(fp.getMunicipality())
                .description(fp.getDescription())
                .latitude(fp.getLatitude())
                .longitude(fp.getLongitude())
                .altitudeM(fp.getAltitudeM())
                .distanceToChannelM(fp.getDistanceToChannelM())
                .basinName(fp.getBasinName())
                .nearbySensorIds(nearbySensorIds)
                .sensorConfig(FloodPointRequestDTO.SensorConfigDTO.builder()
                        .pluviometerStationIds(new java.util.ArrayList<>(fp.getPluviometerStationIds()))
                        .riverLevelStationIds(new java.util.ArrayList<>(fp.getRiverLevelStationIds()))
                        .build())
                .active(fp.getActive())
                .tideHeight(currentTide)
                .tideUnit("m");

        if (includeLiveData) {
            MapiResponseDTO liveData = getPreciseData(fp.getId(), fp.getLatitude(), fp.getLongitude());
            builder.liveData(liveData.getPreciseData());
            builder.floodPrediction(liveData.getFloodPrediction());
        } else {
            // Lista de pontos: também devolve a última previsão gravada (sem chamar a IA).
            builder.floodPrediction(predictionHistory.findLatest(fp.getId(), fp.getLatitude(), fp.getLongitude()).orElse(null));
        }

        return builder.build();
    }

    // Converte do contrato de RESPOSTA ao front (MapiResponseDTO.SensorReadingDTO, camelCase) para
    // o contrato de REQUISIÇÃO à MAPI AI (FloodPredictionRequestDTO.SensorReadingDTO, snake_case) —
    // são a mesma leitura de sensor, mas cada lado da API exige sua própria convenção de nomenclatura.
    private List<com.projeto.mapi.dto.FloodPredictionRequestDTO.SensorReadingDTO> toAiSensorReadings(
            List<MapiResponseDTO.SensorReadingDTO> readings) {
        if (readings == null || readings.isEmpty()) return List.of();
        return readings.stream()
                .map(r -> com.projeto.mapi.dto.FloodPredictionRequestDTO.SensorReadingDTO.builder()
                        .sensorId(r.getSensorId())
                        .latitude(r.getLatitude())
                        .longitude(r.getLongitude())
                        .value(r.getValue())
                        .unit(r.getUnit())
                        .type(r.getType())
                        .timestamp(r.getTimestamp())
                        .distanceKm(r.getDistanceKm())
                        .build())
                .toList();
    }

    private SensorResponseDTO findNearestSensor(double lat, double lon, List<SensorResponseDTO> sensors) {
        return sensors.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .min(Comparator.comparingDouble(s -> GeoUtils.calculateDistance(lat, lon, s.getLatitude(), s.getLongitude())))
                .orElse(null);
    }

    private MapiResponseDTO.PreciseData determinePreciseData(WeatherResponseDTO weather, List<SensorResponseDTO> nearbySensors, Double latitude, Double longitude, Double tideHeight, Double tideTabuaMare, Double waveHeight, Double waveDirection, Double wavePeriod, List<com.projeto.mapi.dto.UnifiedDataDTO> nearestPointHistory) {
        MapiResponseDTO.PreciseData.PreciseDataBuilder builder = MapiResponseDTO.PreciseData.builder();
        
        // Padrão: Dados do Open-Meteo
        builder.source("OPEN_METEO");
        builder.timestamp(LocalDateTime.now());
        
        if (weather != null && weather.current() != null) {
            builder.precipitation(weather.current().precipitation());
            builder.temperature(weather.current().temperature());
            builder.apparentTemperature(weather.current().apparentTemperature());
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

        MapiResponseDTO.Aggregates archiveAggregates = null;

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
            
            // 2. Calcular ACUMULADOS (Aggregates) a partir do histórico do ponto crítico mais
            // próximo, já resolvido uma única vez pelo chamador (getPreciseData) e reaproveitado
            // aqui em vez de repetir a busca do ponto e a exportação de histórico.
            if (nearestPointHistory != null && !nearestPointHistory.isEmpty()) {
                com.projeto.mapi.dto.UnifiedDataDTO last = nearestPointHistory.get(nearestPointHistory.size() - 1);
                archiveAggregates = MapiResponseDTO.Aggregates.builder()
                        .rain3h(last.getAccumulated3h())
                        .rain6h(last.getAccumulated6h())
                        .rain12h(last.getAccumulated12h())
                        .rain24h(last.getAccumulated24h())
                        .rainSource(RAIN_SOURCE_ARCHIVE)
                        .maxRiverLevel24h(last.getSensorWaterLevel()) // Simplificado para o nível regional atual
                        .build();
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

        builder.historicalAggregates(withLiveRain(archiveAggregates, weather));
        return builder.build();
    }

    static final String RAIN_SOURCE_ARCHIVE = "OPEN_METEO_ARCHIVE";
    static final String RAIN_SOURCE_FORECAST = "OPEN_METEO_FORECAST";

    // A grade horária da reanálise (OPEN_METEO_ARCHIVE) fica dias atrás da hora atual, então ao vivo os acumulados
    // vinham nulos e a previsão os imputava como 0,0. Quando a grade não tem a chuva de 24 h, os quatro acumulados
    // passam a vir juntos das últimas 24 h do Open-Meteo forecast, com a origem declarada em rainSource.
    private static MapiResponseDTO.Aggregates withLiveRain(MapiResponseDTO.Aggregates archive, WeatherResponseDTO weather) {
        if (archive != null && archive.getRain24h() != null) return archive;
        if (weather == null || weather.hourly() == null || weather.current() == null || weather.current().time() == null) {
            return archive;
        }
        LocalDateTime now;
        try {
            now = LocalDateTime.parse(weather.current().time());
        } catch (RuntimeException e) {
            return archive;
        }
        Double r24 = com.projeto.mapi.service.weather.RecentRainAccumulator.sumLastHours(weather.hourly(), now, 24);
        if (r24 == null) return archive;
        MapiResponseDTO.Aggregates.AggregatesBuilder b = archive != null ? archive.toBuilder() : MapiResponseDTO.Aggregates.builder();
        return b.rain3h(com.projeto.mapi.service.weather.RecentRainAccumulator.sumLastHours(weather.hourly(), now, 3))
                .rain6h(com.projeto.mapi.service.weather.RecentRainAccumulator.sumLastHours(weather.hourly(), now, 6))
                .rain12h(com.projeto.mapi.service.weather.RecentRainAccumulator.sumLastHours(weather.hourly(), now, 12))
                .rain24h(r24)
                .rainSource(RAIN_SOURCE_FORECAST)
                .build();
    }

    @Override
    public void seedPilotData() {
        if (floodPointRepository.count() > 0) return;
        
        log.info("Semeando pontos piloto de monitoramento...");
        List<FloodPointRequestDTO> pilots = List.of(
            FloodPointRequestDTO.builder()
                .slug("AV_RECIFE_IBURA")
                .name("Av. Recife - Entrada do Ibura")
                .latitude(-8.107910)
                .longitude(-34.927138)
                .sensorConfig(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .pluviometerStationIds(List.of("APAC-PLUVIO-261160615A"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .slug("CIN_UFPE")
                .name("CIn - UFPE")
                .latitude(-8.055310)
                .longitude(-34.951160)
                .sensorConfig(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .pluviometerStationIds(List.of("APAC-PLUVIO-261160601A"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .slug("AGAMENON_DERBY")
                .name("Av. Agamenon Magalhães (Derby)")
                .latitude(-8.052554)
                .longitude(-34.894371)
                .sensorConfig(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .pluviometerStationIds(List.of("APAC-PLUVIO-261160621A"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .slug("JABOATAO_CENTRO")
                .name("Jaboatão Centro (Rio Duas Unas)")
                .latitude(-8.106520)
                .longitude(-35.013210)
                .sensorConfig(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .pluviometerStationIds(List.of("APAC-METEO-260790119H"))
                    .build())
                .build(),
            FloodPointRequestDTO.builder()
                .slug("MASCARENHAS_IMBIRIBEIRA")
                .name("Av. Mascarenhas de Morais")
                .latitude(-8.118123)
                .longitude(-34.904945)
                .sensorConfig(FloodPointRequestDTO.SensorConfigDTO.builder()
                    .pluviometerStationIds(List.of("APAC-PLUVIO-261160609A"))
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

        MapiResponseDTO.PreciseData preciseData = determinePreciseData(weatherData, nearbySensors, latitude, longitude, tideHeight, tideTabuaMare, waveHeight, waveDirection, wavePeriod, List.of());

        // 2. Calcular acumulados de chuva regionais diretamente dos sensores em raio de 3km
        Double acc3h = 0.0, acc6h = 0.0, acc12h = 0.0, acc24h = 0.0;
        try {
            Double[] accWindows = calculateSensorAccumulatedRainfallWindows(latitude, longitude, new int[]{3, 6, 12, 24});
            acc3h = accWindows[0];
            acc6h = accWindows[1];
            acc12h = accWindows[2];
            acc24h = accWindows[3];
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

        return FloodScenarioLabelResponseDTO.builder()
                .id(label.getId())
                .timestamp(label.getTimestamp())
                .latitude(label.getLatitude())
                .longitude(label.getLongitude())
                .isFlooded(label.getIsFlooded())
                .currentRainfall(label.getCurrentRainfall())
                .rainfall3hAccumulated(label.getRainfall3hAccumulated())
                .rainfall6hAccumulated(label.getRainfall6hAccumulated())
                .rainfall12hAccumulated(label.getRainfall12hAccumulated())
                .rainfall24hAccumulated(label.getRainfall24hAccumulated())
                .tideLevel(label.getTideLevel())
                .riverLevel(label.getRiverLevel())
                .windSpeed(label.getWindSpeed())
                .windDirection(label.getWindDirection())
                .temperature(label.getTemperature())
                .apparentTemperature(label.getApparentTemperature())
                .humidity(label.getHumidity())
                .pressure(label.getPressure())
                .waveHeight(label.getWaveHeight())
                .wavePeriod(label.getWavePeriod())
                .waveDirection(label.getWaveDirection())
                .solarRadiation(label.getSolarRadiation())
                .build();
    }

    /**
     * Calcula os acumulados de chuva para múltiplas janelas (ex: 3h/6h/12h/24h) com uma única
     * consulta ao banco (a maior janela), evitando repetir a mesma varredura por raio+tempo no
     * TimescaleDB uma vez por janela como acontecia antes.
     */
    private Double[] calculateSensorAccumulatedRainfallWindows(double lat, double lon, int[] windowsHours) {
        LocalDateTime now = LocalDateTime.now();
        int maxHours = java.util.Arrays.stream(windowsHours).max().orElse(0);
        LocalDateTime maxStart = now.minusHours(maxHours);

        // Buscar todos os dados de sensores na região de 3km cobrindo a maior janela solicitada
        List<SensorData> sensorData = sensorDataRepository.findSensorsByRadius(lat, lon, 3.0, maxStart, now);

        Double[] results = new Double[windowsHours.length];
        for (int i = 0; i < windowsHours.length; i++) {
            LocalDateTime windowStart = now.minusHours(windowsHours[i]);

            // Agrupar por hora (Truncar minutos/segundos) e pegar o valor máximo de precipitação naquela hora
            java.util.Map<LocalDateTime, Double> hourlyMaxPrecip = new java.util.HashMap<>();
            for (SensorData s : sensorData) {
                if (s.getAccumulatedPrecipitation() != null && !s.getTimestamp().isBefore(windowStart)) {
                    LocalDateTime hour = s.getTimestamp().withMinute(0).withSecond(0).withNano(0);
                    double val = s.getAccumulatedPrecipitation();
                    hourlyMaxPrecip.merge(hour, val, Math::max);
                }
            }

            // Somar os máximos de cada hora no período
            double sum = hourlyMaxPrecip.values().stream().mapToDouble(Double::doubleValue).sum();
            results[i] = Math.round(sum * 100.0) / 100.0;
        }
        return results;
    }
}

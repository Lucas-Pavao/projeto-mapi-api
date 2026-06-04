package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.WeatherArchiveResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.model.WeatherData;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.WeatherDataRepository;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.repository.UserRepository;
import com.projeto.mapi.repository.RefreshTokenRepository;
import com.projeto.mapi.service.HistoricalDataService;
import com.projeto.mapi.service.AnaHistoricalService;
import com.projeto.mapi.service.ApacHistoricalService;
import com.projeto.mapi.service.CivilDefenseService;
import com.projeto.mapi.util.ApacStationRegistry;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalDataServiceImpl implements HistoricalDataService {

    private final FloodPointRepository floodPointRepository;
    private final WeatherDataRepository weatherDataRepository;
    private final FloodEventRepository floodEventRepository;
    private final SensorDataRepository sensorDataRepository;
    private final TideTableRepository tideTableRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AnaHistoricalService anaHistoricalService;
    private final ApacHistoricalService apacHistoricalService;
    private final CivilDefenseService civilDefenseService;
    private final com.projeto.mapi.service.MapiService mapiService;
    private final ObjectProvider<HistoricalDataService> serviceProvider;
    private final RestClient restClient = RestClient.builder().baseUrl("https://archive-api.open-meteo.com/v1").build();

    @Override
    @Async("taskExecutor")
    public void ingestHistoricalData(int years) {
        // Garantir que os pontos piloto existam antes da ingestão
        mapiService.seedPilotData();

        List<FloodPoint> points = floodPointRepository.findAll();
        int endYear = LocalDateTime.now().getYear();
        int startYear = endYear - years;

        log.info(">>> Iniciando Ingestão Total ({} anos) para {} pontos monitorados.", years, points.size());

        // 1. Ingestão de Clima (Open-Meteo) para cada ponto
        for (FloodPoint point : points) {
            log.info("Iniciando levas paralelas de CLIMA para o ponto: {}", point.getName());
            ingestPointHistory(point.getSlug(), startYear, endYear);
        }

        // 2. Ingestão de Sensores ANA (Por ID da Estação vinculado ao ponto)
        for (FloodPoint point : points) {
            if (point.getPluviometerStationId() != null && !point.getPluviometerStationId().contains("APAC")) {
                anaHistoricalService.ingestHistoricalSensorData(point.getPluviometerStationId(), years);
            }
        }

        // 3. Ingestão de Sensores APAC (Otimizada: Baixa todos os municípios de uma vez)
        log.info(">>> Iniciando ingestão em lote de sensores APAC (Estado de PE)...");
        for (int y = startYear; y <= endYear; y++) {
            apacHistoricalService.ingestFullStateRainfall(y);
        }

        civilDefenseService.ingestLastYears(years);
        log.info(">>> Ingestão de dados da Defesa Civil concluída.");
        
        // Chamada via proxy para respeitar o @Transactional
        log.info(">>> Iniciando correção automática de viés de horário para alagamentos...");
        serviceProvider.getIfAvailable().alignFloodEventsToRainPeaks();
        log.info(">>> Processo Global de Ingestão e Alinhamento Finalizado.");
    }

    @Override
    @Transactional
    public void alignFloodEventsToRainPeaks() {
        log.info("Iniciando Alinhamento de Alagamentos ao Pico de Chuva Regional (Raio 5km)...");
        List<com.projeto.mapi.model.FloodEvent> events = floodEventRepository.findAll();
        
        for (com.projeto.mapi.model.FloodEvent event : events) {
            // Se o evento começa à meia-noite (comum em dados sem hora), tentamos alinhar
            if (event.getStartTime().getHour() == 0 && event.getStartTime().getMinute() == 0) {
                LocalDateTime dayStart = event.getStartTime().withHour(0).withMinute(0);
                LocalDateTime dayEnd = event.getStartTime().withHour(23).withMinute(59);
                
                FloodPoint point = event.getFloodPoint();

                // 1. Buscar o pico de chuva REGIONAL nos sensores (ANA/CEMADEN/APAC)
                List<SensorData> nearbySensors = sensorDataRepository.findSensorsByRadius(
                    point.getLatitude(), point.getLongitude(), 5.0, dayStart, dayEnd);
                
                LocalDateTime peakTime = null;
                double maxPrecip = 0.0;

                if (!nearbySensors.isEmpty()) {
                    // Encontrar o timestamp com maior precipitação acumulada na região
                    SensorData peakSensor = nearbySensors.stream()
                        .filter(s -> s.getAccumulatedPrecipitation() != null)
                        .max(java.util.Comparator.comparingDouble(SensorData::getAccumulatedPrecipitation))
                        .orElse(null);
                    
                    if (peakSensor != null && peakSensor.getAccumulatedPrecipitation() > 0) {
                        peakTime = peakSensor.getTimestamp();
                        maxPrecip = peakSensor.getAccumulatedPrecipitation();
                    }
                }

                // 2. Se não houver sensores, usar Open-Meteo como fallback
                if (peakTime == null) {
                    List<WeatherData> weather = weatherDataRepository.findByLatitudeAndLongitudeAndTimestampBetween(
                        point.getLatitude(), point.getLongitude(), dayStart, dayEnd);
                    
                    WeatherData peakWeather = weather.stream()
                        .filter(w -> w.getPrecipitation() != null)
                        .max(java.util.Comparator.comparingDouble(WeatherData::getPrecipitation))
                        .orElse(null);
                    
                    if (peakWeather != null && peakWeather.getPrecipitation() > 0) {
                        peakTime = peakWeather.getTimestamp();
                        maxPrecip = peakWeather.getPrecipitation();
                    }
                }

                // 3. Aplicar alinhamento se um pico foi encontrado
                if (peakTime != null) {
                    log.info("Alinhando evento em {} (slug: {}). Pico de {}mm as {}", 
                        point.getName(), point.getSlug(), maxPrecip, peakTime.getHour());
                    event.setStartTime(peakTime);
                    // Definimos uma janela de impacto padrão de 3 horas após o pico
                    event.setEndTime(peakTime.plusHours(3));
                    floodEventRepository.save(event);
                }
            }
        }
        log.info("Alinhamento regional concluído.");
    }

    private void processPointFullHistory(FloodPoint point, int startYear, int endYear, int years) {
        // Método mantido para compatibilidade mas o fluxo principal agora é via loop otimizado em ingestHistoricalData
        ingestPointHistory(point.getSlug(), startYear, endYear);
    }

    @Override
    public void ingestApacHistoricalRainfall(String stationCode, int year) {
        apacHistoricalService.ingestHistoricalRainfall(stationCode, year);
    }

    @Override
    public void ingestApacFullStateRainfall(int year) {
        apacHistoricalService.ingestFullStateRainfall(year);
    }

    @Override
    public void ingestHistoricalSensors(int years) {
        mapiService.seedPilotData();
        
        // ANA
        List<FloodPoint> points = floodPointRepository.findAll();
        for (FloodPoint point : points) {
            if (point.getPluviometerStationId() != null && !point.getPluviometerStationId().contains("APAC")) {
                anaHistoricalService.ingestHistoricalSensorData(point.getPluviometerStationId(), years);
            }
        }

        // APAC (Em lote)
        for (int y = LocalDateTime.now().getYear() - years; y <= LocalDateTime.now().getYear(); y++) {
            apacHistoricalService.ingestFullStateRainfall(y);
        }
    }

    @Override
    public void ingestPointHistory(String slug, int startYear, int endYear) {
        FloodPoint point = floodPointRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Ponto não encontrado: " + slug));

        for (int year = startYear; year <= endYear; year++) {
            try {
                fetchAndSaveYear(point, year);
            } catch (Exception e) {
                log.error("Erro ao processar ano {} para ponto {}: {}", year, slug, e.getMessage());
            }
        }
    }

    private void fetchAndSaveYear(FloodPoint point, int year) {
        LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year, 12, 31, 23, 59);
        
        // Verifica se já temos dados para este ano
        List<WeatherData> existing = weatherDataRepository.findByLatitudeAndLongitudeAndTimestampBetween(
            point.getLatitude(), point.getLongitude(), startOfYear, endOfYear);
        
        if (existing.size() > 8000) { // Um ano tem ~8760 horas
            log.info("Pulando ano {} para ponto {}: Já existem {} registros.", year, point.getSlug(), existing.size());
            return;
        }

        final String startDate = year + "-01-01";
        final String endDateStr = (year == LocalDateTime.now().getYear()) ? 
            LocalDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE) : year + "-12-31";

        log.info("Buscando clima do Open-Meteo para {} no ano {}...", point.getSlug(), year);
        try {
            WeatherArchiveResponseDTO response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/archive")
                            .queryParam("latitude", point.getLatitude())
                            .queryParam("longitude", point.getLongitude())
                            .queryParam("start_date", startDate)
                            .queryParam("end_date", endDateStr)
                            .queryParam("hourly", "temperature_2m,relative_humidity_2m,surface_pressure,precipitation,weather_code")
                            .build())
                    .retrieve()
                    .body(WeatherArchiveResponseDTO.class);

            if (response != null && response.hourly() != null) {
                saveDataBatch(response, point.getLatitude(), point.getLongitude());
            } else {
                log.warn("Open-Meteo retornou corpo vazio para {} em {}", point.getSlug(), year);
            }
        } catch (Exception e) {
            log.error("Erro na API do Open-Meteo para {} em {}: {}", point.getSlug(), year, e.getMessage());
        }
    }

    private void saveDataBatch(WeatherArchiveResponseDTO response, Double requestedLat, Double requestedLon) {
        var hourly = response.hourly();
        List<WeatherData> batch = new ArrayList<>();
        for (int i = 0; i < hourly.time().size(); i++) {
            batch.add(WeatherData.builder()
                    .latitude(requestedLat)
                    .longitude(requestedLon)
                    .timestamp(LocalDateTime.parse(hourly.time().get(i), DateTimeFormatter.ISO_DATE_TIME))
                    .precipitation(hourly.precipitation().get(i))
                    .temperature(hourly.temperature().get(i))
                    .humidity(hourly.humidity() != null ? hourly.humidity().get(i) : null)
                    .pressure(hourly.pressure() != null ? hourly.pressure().get(i) : null)
                    .weatherCode(hourly.weatherCode().get(i))
                    .createdAt(LocalDateTime.now())
                    .build());
            if (batch.size() >= 1000) {
                weatherDataRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            weatherDataRepository.saveAll(batch);
        }
        log.info("Persistidos {} registros de clima para {}, {}", hourly.time().size(), requestedLat, requestedLon);
    }

    @Override
    public List<com.projeto.mapi.dto.DataHealthReportDTO> checkDataIntegrity() {
        List<FloodPoint> points = floodPointRepository.findAll();
        List<com.projeto.mapi.dto.DataHealthReportDTO> reports = new ArrayList<>();

        for (FloodPoint point : points) {
            long weatherCount = weatherDataRepository.countByLatitudeAndLongitude(point.getLatitude(), point.getLongitude());
            
            // Detalhamento Clima por Ano
            Map<Integer, Long> weatherByYear = new java.util.TreeMap<>();
            weatherDataRepository.countByLatitudeAndLongitudeGroupedByYear(point.getLatitude(), point.getLongitude())
                .forEach(row -> weatherByYear.put((Integer) row[0], (Long) row[1]));

            long sensorCount = 0;
            Map<Integer, Long> sensorByYear = new java.util.TreeMap<>();
            
            if (point.getPluviometerStationId() != null) {
                sensorCount += sensorDataRepository.countBySensorId(point.getPluviometerStationId());
                sensorDataRepository.countBySensorIdGroupedByYear(point.getPluviometerStationId())
                    .forEach(row -> sensorByYear.merge((Integer) row[0], (Long) row[1], Long::sum));
            }
            
            // Também conta registros salvos diretamente com o slug do ponto (como arquivos locais)
            sensorCount += sensorDataRepository.countBySensorId(point.getSlug());
            sensorDataRepository.countBySensorIdGroupedByYear(point.getSlug())
                .forEach(row -> sensorByYear.merge((Integer) row[0], (Long) row[1], Long::sum));
            
            long eventCount = floodEventRepository.countByFloodPointId(point.getId());
            
            reports.add(com.projeto.mapi.dto.DataHealthReportDTO.builder()
                    .slug(point.getSlug())
                    .totalWeatherRecords(weatherCount)
                    .totalSensorRecords(sensorCount)
                    .totalFloodEvents(eventCount)
                    .weatherRecordsByYear(weatherByYear)
                    .sensorRecordsByYear(sensorByYear)
                    .status(weatherCount > 30000 ? "OK" : "INCOMPLETE")
                    .build());
        }
        return reports;
    }

    @Override
    @Transactional
    public void wipeDatabase() {
        log.info("LIMPANDO BANCO DE DADOS (Wipe 100%)...");
        
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        floodEventRepository.deleteAll();
        sensorDataRepository.deleteAll();
        weatherDataRepository.deleteAll();
        tideTableRepository.deleteAll();
        floodPointRepository.deleteAll();
        
        log.info("Banco de dados limpo com sucesso (Usuários, Eventos, Sensores, Clima, Marés e Pontos).");
    }

    @Override
    @Transactional
    public void repairStationMappings() {
        log.info("Iniciando reparo de coordenadas de sensores e mapeamentos...");
        
        // 1. Reparar coordenadas nulas usando o registro estático
        repairSensorCoordinates();

        log.info("Reparando mapeamentos de estações pluviométricas para todos os pontos cadastrados via proximidade...");
        
        List<FloodPoint> points = floodPointRepository.findAll();
        List<SensorData> latestSensors = sensorDataRepository.findAllLatest();
        
        if (latestSensors.isEmpty()) {
            log.warn("Nenhum dado de sensor encontrado no banco para realizar o mapeamento por proximidade.");
            return;
        }

        // 2. Mapeamento de estações meteorológicas (sem coordenadas) para todos os pontos
        List<SensorData> weatherStations = latestSensors.stream()
            .filter(s -> s.getLatitude() == null || s.getLongitude() == null)
            .toList();

        for (SensorData ws : weatherStations) {
            String wsId = ws.getSensorId() != null && ws.getSensorId().length() >= 3 ? ws.getSensorId() : ws.getCode();
            if (wsId == null) continue;
            for (FloodPoint point : points) {
                if (point.getWeatherStationIds() == null) {
                    point.setWeatherStationIds(new java.util.HashSet<>());
                }
                point.getWeatherStationIds().add(wsId);
            }
        }

        // 3. Mapeamento por proximidade para estações com coordenadas
        for (FloodPoint point : points) {
            if (point.getLatitude() == null || point.getLongitude() == null) {
                floodPointRepository.save(point); // Salva as atualizações das weather stations
                continue;
            }

            SensorData nearest = null;
            double minDistance = Double.MAX_VALUE;

            for (SensorData sensor : latestSensors) {
                if (sensor.getLatitude() == null || sensor.getLongitude() == null) continue;
                
                double dist = GeoUtils.calculateDistance(
                    point.getLatitude(), point.getLongitude(),
                    sensor.getLatitude(), sensor.getLongitude()
                );
                
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = sensor;
                }
            }

            if (nearest != null && minDistance < 5.0) { // Limite de 5km para consistência regional
                String stationId = nearest.getSensorId();
                // Se for um sensor genérico sem ID amigável, usa o código técnico
                if (stationId == null || stationId.length() < 3) stationId = nearest.getCode();
                
                point.setPluviometerStationId(stationId);
                floodPointRepository.save(point);
                log.info("Ponto {} mapeado para a estação mais próxima: {} (Distância: {} km)", 
                    point.getSlug(), stationId, String.format("%.2f", minDistance));
            } else {
                floodPointRepository.save(point); // Salva as atualizações das weather stations mesmo sem pluviômetro próximo
                log.warn("Nenhuma estação encontrada em um raio de 5km para o ponto {}. Distância mínima: {} km", 
                    point.getSlug(), nearest != null ? String.format("%.2f", minDistance) : "N/A");
            }
        }
    }

    private void repairSensorCoordinates() {
        log.info("---- Corrigindo coordenadas nulas de sensores APAC no banco...");
        List<SensorData> allSensors = sensorDataRepository.findAll();
        List<SensorData> sensorsToUpdate = new ArrayList<>();

        for (SensorData s : allSensors) {
            if (s.getLatitude() == null || s.getLongitude() == null) {
                ApacStationRegistry.StationMetadata meta = ApacStationRegistry.getMetadata(s.getCode());
                if (meta == null) meta = ApacStationRegistry.findByName(s.getStationName());

                if (meta != null) {
                    s.setLatitude(meta.getLatitude());
                    s.setLongitude(meta.getLongitude());
                    sensorsToUpdate.add(s);
                }
            }
        }
        
        if (!sensorsToUpdate.isEmpty()) {
            sensorDataRepository.saveAll(sensorsToUpdate);
            log.info("---- Sucesso: {} registros de sensores tiveram suas coordenadas corrigidas.", sensorsToUpdate.size());
        } else {
            log.info("---- Nenhum registro precisou de correção ou metadados não encontrados.");
        }
    }

    @Override
    @Async
    public void ingestCivilDefenseData(String resourceId) {
        civilDefenseService.ingestFloodEvents(resourceId);
    }

    @Override
    @Async
    public void ingestCivilDefenseLastYears(int years) {
        civilDefenseService.ingestLastYears(years);
    }
}

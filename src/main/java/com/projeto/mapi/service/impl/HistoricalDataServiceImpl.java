package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.WeatherArchiveResponseDTO;
import com.projeto.mapi.model.FloodPoint;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final RestClient restClient = RestClient.builder().baseUrl("https://archive-api.open-meteo.com/v1").build();

    @Override
    @Async("taskExecutor")
    public void ingestHistoricalData(int years) {
        List<FloodPoint> points = floodPointRepository.findAll();
        int endYear = LocalDateTime.now().getYear();
        int startYear = endYear - years;

        log.info(">>> Iniciando Ingestão Total ({} anos) para {} pontos monitorados.", years, points.size());

        for (FloodPoint point : points) {
            // Cada ponto será processado de forma independente (paralelismo aqui precisaria de mais refatoração, 
            // mas o @Async no método já libera o Controller)
            processPointFullHistory(point, startYear, endYear, years);
        }
        
        civilDefenseService.ingestLastYears(years);
        log.info(">>> Comando de Ingestão Total disparado.");
    }

    private void processPointFullHistory(FloodPoint point, int startYear, int endYear, int years) {
        log.info("Iniciando levas paralelas para o ponto: {}", point.getName());
        
        // Clima
        ingestPointHistory(point.getSlug(), startYear, endYear);
        
        // Sensores ANA
        if (point.getPluviometerStationId() != null && !point.getPluviometerStationId().contains("APAC")) {
            anaHistoricalService.ingestHistoricalSensorData(point.getPluviometerStationId(), years);
        }

        // Sensores APAC
        if (point.getPluviometerStationId() != null && point.getPluviometerStationId().contains("APAC")) {
            String code = point.getPluviometerStationId().replace("APAC-PLUVIO-", "");
            for (int y = startYear; y <= endYear; y++) {
                apacHistoricalService.ingestHistoricalRainfall(code, y);
            }
        }
    }

    @Override
    public void ingestApacHistoricalRainfall(String stationCode, int year) {
        apacHistoricalService.ingestHistoricalRainfall(stationCode, year);
    }

    @Override
    @Async
    public void ingestHistoricalSensors(int years) {
        List<FloodPoint> points = floodPointRepository.findAll();
        for (FloodPoint point : points) {
            if (point.getPluviometerStationId() != null) {
                if (point.getPluviometerStationId().contains("APAC")) {
                    String code = point.getPluviometerStationId().replace("APAC-PLUVIO-", "");
                    apacHistoricalService.ingestHistoricalRainfall(code, LocalDateTime.now().getYear());
                } else {
                    anaHistoricalService.ingestHistoricalSensorData(point.getPluviometerStationId(), years);
                }
            }
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
                            .queryParam("hourly", "precipitation,temperature_2m,relative_humidity_2m,weather_code")
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
                    .humidity(hourly.humidity().get(i))
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
        log.info("Reparando mapeamentos de estações pluviométricas para pontos piloto (Usando ANA 00834003 estável)...");
        String stableAna = "00834003";
        updateStation("AV_RECIFE_IBURA", stableAna);
        updateStation("CIN_UFPE", stableAna);
        updateStation("AGAMENON_DERBY", stableAna);
        updateStation("JABOATAO_CENTRO", stableAna);
        updateStation("MASCARENHAS_IMBIRIBEIRA", stableAna);
    }

    private void updateStation(String slug, String stationId) {
        floodPointRepository.findBySlug(slug).ifPresent(p -> {
            p.setPluviometerStationId(stationId);
            floodPointRepository.save(p);
            log.info("Ponto {} atualizado para estação {}", slug, stationId);
        });
    }

    @Override
    @Transactional
    public void ingestLocalArchives() {
        log.info("Iniciando ingestão de arquivos JSON locais...");
        String[] files = {"ibura_2022.json", "derby_2022.json", "derby_2023.json", "jaboatao_2022.json", "mascarenhas_2022.json", "mascarenhas_2023.json", "ibura_2023.json"};
        
        for (String fileName : files) {
            java.io.File file = new java.io.File(fileName);
            if (file.exists()) {
                log.info("Processando arquivo local: {}", fileName);
                try {
                    String content = java.nio.file.Files.readString(file.toPath());
                    com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
                    String slug = fileName.split("_")[0].toUpperCase();
                    if (slug.equals("IBURA")) slug = "AV_RECIFE_IBURA";
                    if (slug.equals("DERBY")) slug = "AGAMENON_DERBY";
                    if (slug.equals("JABOATAO")) slug = "JABOATAO_CENTRO";
                    if (slug.equals("MASCARENHAS")) slug = "MASCARENHAS_IMBIRIBEIRA";
                    
                    final String finalSlug = slug;
                    var hourly = root.get("hourly");
                    var times = hourly.get("time");
                    var precip = hourly.get("precipitation");
                    
                    List<com.projeto.mapi.model.SensorData> batch = new ArrayList<>();
                    for (int i = 0; i < times.size(); i++) {
                        LocalDateTime ts = LocalDateTime.parse(times.get(i).asText());
                        if (sensorDataRepository.findBySensorIdAndTimestamp(finalSlug, ts).isEmpty()) {
                            batch.add(com.projeto.mapi.model.SensorData.builder()
                                    .sensorId(finalSlug)
                                    .timestamp(ts)
                                    .accumulatedPrecipitation(precip.get(i).asDouble())
                                    .source("LOCAL_ARCHIVE")
                                    .unit("mm")
                                    .build());
                        }
                        if (batch.size() >= 500) {
                            sensorDataRepository.saveAll(batch);
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) sensorDataRepository.saveAll(batch);
                    log.info("Ingeridos {} registros de sensor a partir de {}", times.size(), fileName);
                } catch (Exception e) {
                    log.error("Erro ao processar arquivo local {}: {}", fileName, e.getMessage());
                }
            }
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

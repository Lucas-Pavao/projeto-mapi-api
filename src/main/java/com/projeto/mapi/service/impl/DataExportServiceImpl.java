package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.model.*;
import com.projeto.mapi.repository.*;
import com.projeto.mapi.service.DataExportService;
import com.projeto.mapi.service.TideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class DataExportServiceImpl implements DataExportService {

    private final FloodPointRepository floodPointRepository;
    private final SensorDataRepository sensorDataRepository;
    private final WeatherDataRepository weatherDataRepository;
    private final FloodEventRepository floodEventRepository;
    private final TideService tideService;

    public List<com.projeto.mapi.model.FloodPoint> getPoints() {
        return floodPointRepository.findAll();
    }

    @Override
    public List<UnifiedDataDTO> exportAllPointsData(int days) {
        List<FloodPoint> points = floodPointRepository.findAll();
        
        // Otimização: Paralelismo para processar vários pontos ao mesmo tempo
        return points.parallelStream()
                .flatMap(point -> exportUnifiedData(point.getSlug(), days).stream())
                .collect(Collectors.toList());
    }

    @Override
    public List<UnifiedDataDTO> exportUnifiedData(String slug, int days) {
        FloodPoint point = floodPointRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Ponto não encontrado: " + slug));

        // Define o intervalo de busca
        LocalDateTime end = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime start = (days > 0) ? end.minusDays(days) : LocalDateTime.of(2021, 1, 1, 0, 0);
// --- NOVA LÓGICA ESPACIAL: Busca regional em raio de 3km ---
log.info("---- Exportando dados regionais (Raio 3km) para o ponto {}", slug);

List<SensorData> sensorData = sensorDataRepository.findSensorsByRadius(
        point.getLatitude(), point.getLongitude(), 3.0, start, end);
        
        // Incluir os dados das estações meteorológicas vinculadas globalmente ao ponto
        if (point.getWeatherStationIds() != null && !point.getWeatherStationIds().isEmpty()) {
            List<SensorData> weatherStationsData = sensorDataRepository.findBySensorIdInAndTimestampBetween(
                point.getWeatherStationIds(), start, end);
            sensorData.addAll(weatherStationsData);
            log.info("---- Adicionados {} registros de estações meteorológicas vinculadas globalmente.", weatherStationsData.size());
        }

        log.info("---- Encontrados {} registros de sensores (total) na região do ponto {}.", sensorData.size(), slug);
        
        List<WeatherData> weatherData = weatherDataRepository.findByLatitudeAndLongitudeAndTimestampBetween(
                point.getLatitude(), point.getLongitude(), start, end);
        
        List<FloodEvent> events = floodEventRepository.findByFloodPointId(point.getId());

        // 2. Criar Timeline contínua (Hora a Hora)
        Map<LocalDateTime, UnifiedDataDTO.UnifiedDataDTOBuilder> timeline = new TreeMap<>();
        LocalDateTime current = start;
        while (!current.isAfter(end)) {
            timeline.put(current, createInitialBuilder(slug, current));
            current = current.plusHours(1);
        }

        // 3. Mapear dados para a timeline com AGREGAÇÃO REGIONAL (Max Rainfall para segurança)
        sensorData.forEach(s -> {
            LocalDateTime hour = s.getTimestamp().withMinute(0).withSecond(0).withNano(0);
            if (timeline.containsKey(hour)) {
                UnifiedDataDTO.UnifiedDataDTOBuilder builder = timeline.get(hour);
                
                // Agregação de Precipitação: Pega o valor MÁXIMO detectado na região naquela hora
                if (s.getAccumulatedPrecipitation() != null) {
                    builder.sensorPrecipitation(Math.max(
                        builder.build().getSensorPrecipitation() != null ? builder.build().getSensorPrecipitation() : 0.0,
                        s.getAccumulatedPrecipitation()
                    ));
                }

                // Agregação de Nível: Pega o máximo da região
                if (s.getWaterLevel() != null) {
                    builder.sensorWaterLevel(Math.max(
                        builder.build().getSensorWaterLevel() != null ? builder.build().getSensorWaterLevel() : 0.0,
                        s.getWaterLevel()
                    ));
                }
                
                // Tratar umidade do solo regional (Média)
                if (s.getSoilHumidity() != null) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode soilNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(s.getSoilHumidity());
                        double currentSoil = 0.0;
                        if (soilNode.isArray() && soilNode.size() > 0) currentSoil = soilNode.get(0).asDouble();
                        else if (soilNode.isNumber()) currentSoil = soilNode.asDouble();
                        
                        builder.sensorSoilHumidity(currentSoil); // Simplificado: sobrescreve
                    } catch (Exception ignored) {}
                }
            }
        });

        weatherData.forEach(w -> {
            LocalDateTime hour = w.getTimestamp().withMinute(0).withSecond(0).withNano(0);
            if (timeline.containsKey(hour)) {
                timeline.get(hour)
                        .weatherPrecipitation(w.getPrecipitation())
                        .weatherTemperature(w.getTemperature())
                        .weatherPressure(w.getPressure())
                        .weatherCode(w.getWeatherCode());
            }
        });

        // 5. Processar e Refinar (Tide + Flood Labels)
        // Otimização: Cache de maré por hora para evitar chamadas repetidas à API externa se o porto for o mesmo
        java.util.concurrent.ConcurrentHashMap<LocalDateTime, Double> tideCache = new java.util.concurrent.ConcurrentHashMap<>();

        List<UnifiedDataDTO> result = timeline.entrySet().stream().map(entry -> {
            LocalDateTime ts = entry.getKey();
            UnifiedDataDTO.UnifiedDataDTOBuilder builder = entry.getValue();

            // Altura da Maré (Otimizado com cache local na exportação)
            try {
                Double tide = tideCache.computeIfAbsent(ts, t -> 
                    tideService.getTideHeightAt(point.getLatitude(), point.getLongitude(), t));
                
                if (tide != null) {
                    tide = Math.round(tide * 100.0) / 100.0;
                }
                builder.tideHeight(tide);
            } catch (Exception e) {
                builder.tideHeight(null);
            }

            // Label de Alagamento (Ground Truth)
            // Otimização: Filtra apenas eventos que podem sobrepor o timestamp atual
            boolean flooded = events.stream().anyMatch(e -> isTimeWithinEvent(ts, e));
            builder.isFlooded(flooded);
            if (flooded) {
                events.stream()
                        .filter(e -> isTimeWithinEvent(ts, e))
                        .findFirst()
                        .ifPresent(e -> builder.severity(e.getSeverity() != null ? e.getSeverity().name() : "MEDIUM"));
            }

            return builder.build();
        }).collect(Collectors.toList());

        return fillMissingValues(result);
    }

    @Override
    public List<UnifiedDataDTO> exportUnifiedDataWithAccumulated(String slug, int days) {
        List<UnifiedDataDTO> baseData = exportUnifiedData(slug, days);
        
        for (int i = 0; i < baseData.size(); i++) {
            baseData.get(i).setAccumulated3h(calculateSum(baseData, i, 3));
            baseData.get(i).setAccumulated6h(calculateSum(baseData, i, 6));
            baseData.get(i).setAccumulated12h(calculateSum(baseData, i, 12));
            baseData.get(i).setAccumulated24h(calculateSum(baseData, i, 24));
            baseData.get(i).setAccumulated48h(calculateSum(baseData, i, 48));
        }
        
        return baseData;
    }

    private Double calculateSum(List<UnifiedDataDTO> data, int index, int hours) {
        double sum = 0.0;
        int count = 0;
        for (int i = index; i >= 0 && count < hours; i--) {
            Double val = data.get(i).getWeatherPrecipitation();
            sum += (val != null ? val : 0.0);
            count++;
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    private List<UnifiedDataDTO> fillMissingValues(List<UnifiedDataDTO> data) {
        if (data.isEmpty()) return data;

        Double lastWeatherP = 0.0;
        Double lastTemp = 25.0; // Média padrão Recife
        Double lastPressure = 1013.0; // Padrão atm
        Integer lastCode = 0;
        Double lastSoil = 30.0; // Padrão úmido
        Double lastWater = 0.0;

        for (UnifiedDataDTO d : data) {
            // Se sensor é null, assume 0 (ausência de chuva detectada)
            if (d.getSensorPrecipitation() == null) d.setSensorPrecipitation(0.0);

            if (d.getSensorWaterLevel() == null) d.setSensorWaterLevel(lastWater);
            else lastWater = d.getSensorWaterLevel();

            if (d.getSensorSoilHumidity() == null) d.setSensorSoilHumidity(lastSoil);
            else lastSoil = d.getSensorSoilHumidity();

            // Se clima é null, assume 0 para chuva e preenche temperatura/pressão com a última leitura conhecida
            if (d.getWeatherPrecipitation() == null) d.setWeatherPrecipitation(0.0);

            if (d.getWeatherTemperature() == null) d.setWeatherTemperature(lastTemp);
            else lastTemp = d.getWeatherTemperature();

            if (d.getWeatherPressure() == null) d.setWeatherPressure(lastPressure);
            else lastPressure = d.getWeatherPressure();

            if (d.getWeatherCode() == null) d.setWeatherCode(lastCode);
            else lastCode = d.getWeatherCode();

            if (d.getIsFlooded() == null) d.setIsFlooded(false);
        }
        return data;
    }

    private UnifiedDataDTO.UnifiedDataDTOBuilder createInitialBuilder(String slug, LocalDateTime ts) {
        return UnifiedDataDTO.builder()
                .floodPointSlug(slug)
                .timestamp(ts)
                .isFlooded(false)
                .severity(null);
    }

    private boolean isTimeWithinEvent(LocalDateTime ts, FloodEvent e) {
        LocalDateTime eventStart = e.getStartTime().withMinute(0).withSecond(0).withNano(0);
        // Se não houver data fim (comum na Defesa Civil), assumimos uma janela de impacto de 3 horas
        LocalDateTime eventEnd = e.getEndTime() != null ? e.getEndTime() : e.getStartTime().plusHours(3);
        eventEnd = eventEnd.withMinute(59).withSecond(59);
        
        return (ts.isAfter(eventStart) || ts.isEqual(eventStart)) && (ts.isBefore(eventEnd) || ts.isEqual(eventEnd));
    }

    @Override
    public String generateCsv(List<UnifiedDataDTO> data) {
        StringBuilder csv = new StringBuilder();
        csv.append("floodPointSlug,timestamp,sensorPrecipitation,sensorWaterLevel,sensorSoilHumidity,weatherPrecipitation,weatherTemperature,weatherPressure,weatherCode,tideHeight,accumulated3h,accumulated6h,accumulated12h,accumulated24h,accumulated48h,isFlooded,severity\n");
        
        for (UnifiedDataDTO d : data) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    d.getFloodPointSlug(),
                    d.getTimestamp(),
                    d.getSensorPrecipitation(),
                    d.getSensorWaterLevel(),
                    d.getSensorSoilHumidity(),
                    d.getWeatherPrecipitation(),
                    d.getWeatherTemperature(),
                    d.getWeatherPressure(),
                    d.getWeatherCode(),
                    d.getTideHeight(),
                    d.getAccumulated3h(),
                    d.getAccumulated6h(),
                    d.getAccumulated12h(),
                    d.getAccumulated24h(),
                    d.getAccumulated48h(),
                    d.getIsFlooded(),
                    d.getSeverity()
            ));
        }
        return csv.toString();
    }

    @Override
    public void streamUnifiedDataToOutputStream(java.io.OutputStream outputStream, int days) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        try (com.fasterxml.jackson.core.JsonGenerator jg = mapper.getFactory().createGenerator(outputStream)) {
            jg.writeStartArray();
            
            List<FloodPoint> points = floodPointRepository.findAll();
            for (FloodPoint point : points) {
                List<UnifiedDataDTO> data = exportUnifiedDataWithAccumulated(point.getSlug(), days);
                for (UnifiedDataDTO d : data) {
                    jg.writeObject(d);
                    jg.flush(); // Envia para o stream imediatamente
                }
            }
            
            jg.writeEndArray();
        } catch (Exception e) {
            log.error("Erro ao fazer streaming de dados para IA: {}", e.getMessage());
            throw new RuntimeException("Erro na exportação de dados", e);
        }
    }
}

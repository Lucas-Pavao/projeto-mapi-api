package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.model.*;
import com.projeto.mapi.repository.*;
import com.projeto.mapi.service.DataExportService;
import com.projeto.mapi.service.TideService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataExportServiceImpl implements DataExportService {

    private final FloodPointRepository floodPointRepository;
    private final SensorDataRepository sensorDataRepository;
    private final WeatherDataRepository weatherDataRepository;
    private final FloodEventRepository floodEventRepository;
    private final TideService tideService;

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

        // 1. Buscar todos os dados brutos
        List<SensorData> sensorData = sensorDataRepository.findBySensorIdAndTimestampBetween(
                point.getPluviometerStationId(), start, end);
        
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

        // 3. Mapear dados para a timeline (Agrupamento por hora mais próxima)
        sensorData.forEach(s -> {
            LocalDateTime hour = s.getTimestamp().withMinute(0).withSecond(0).withNano(0);
            if (timeline.containsKey(hour)) {
                timeline.get(hour).sensorPrecipitation(s.getAccumulatedPrecipitation());
            }
        });

        weatherData.forEach(w -> {
            LocalDateTime hour = w.getTimestamp().withMinute(0).withSecond(0).withNano(0);
            if (timeline.containsKey(hour)) {
                timeline.get(hour)
                        .weatherPrecipitation(w.getPrecipitation())
                        .weatherTemperature(w.getTemperature())
                        .weatherCode(w.getWeatherCode());
            }
        });

        // 4. Processar e Refinar (Tide + Flood Labels)
        // Otimização: Cache de maré por hora para evitar chamadas repetidas à API externa se o porto for o mesmo
        java.util.concurrent.ConcurrentHashMap<LocalDateTime, Double> tideCache = new java.util.concurrent.ConcurrentHashMap<>();

        List<UnifiedDataDTO> result = timeline.entrySet().stream().map(entry -> {
            LocalDateTime ts = entry.getKey();
            UnifiedDataDTO.UnifiedDataDTOBuilder builder = entry.getValue();

            // Altura da Maré (Otimizado com cache local na exportação)
            try {
                Double tide = tideCache.computeIfAbsent(ts, t -> 
                    tideService.getTideHeightAt(point.getLatitude(), point.getLongitude(), t));
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

    private List<UnifiedDataDTO> fillMissingValues(List<UnifiedDataDTO> data) {
        if (data.isEmpty()) return data;

        Double lastSensorP = 0.0;
        Double lastWeatherP = 0.0;
        Double lastTemp = 25.0; // Média padrão Recife
        Integer lastCode = 0;

        for (UnifiedDataDTO d : data) {
            // Se sensor é null, assume 0 (ausência de chuva detectada) ou mantém último
            if (d.getSensorPrecipitation() == null) d.setSensorPrecipitation(0.0);
            else lastSensorP = d.getSensorPrecipitation();

            // Se clima é null, preenche com a última leitura conhecida (Forward Fill)
            if (d.getWeatherPrecipitation() == null) d.setWeatherPrecipitation(lastWeatherP);
            else lastWeatherP = d.getWeatherPrecipitation();

            if (d.getWeatherTemperature() == null) d.setWeatherTemperature(lastTemp);
            else lastTemp = d.getWeatherTemperature();

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
        csv.append("floodPointSlug,timestamp,sensorPrecipitation,weatherPrecipitation,weatherTemperature,weatherCode,tideHeight,isFlooded,severity\n");
        
        for (UnifiedDataDTO d : data) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    d.getFloodPointSlug(),
                    d.getTimestamp(),
                    d.getSensorPrecipitation(),
                    d.getWeatherPrecipitation(),
                    d.getWeatherTemperature(),
                    d.getWeatherCode(),
                    d.getTideHeight(),
                    d.getIsFlooded(),
                    d.getSeverity()
            ));
        }
        return csv.toString();
    }
}

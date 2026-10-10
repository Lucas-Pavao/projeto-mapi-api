package com.projeto.mapi.service.export.impl;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.model.*;
import com.projeto.mapi.repository.*;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.tide.TideService;
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
    private final com.projeto.mapi.repository.ingestion.ObservationReadRepository observationRead;
    private final com.projeto.mapi.config.IngestionProperties ingestionProps;
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
    public List<UnifiedDataDTO> exportAllPointsDataWithAccumulated(int days) {
        List<FloodPoint> points = floodPointRepository.findAll();

        return points.parallelStream()
                .flatMap(point -> exportUnifiedDataWithAccumulated(point.getSlug(), days).stream())
                .collect(Collectors.toList());
    }

    @Override
    public List<UnifiedDataDTO> exportUnifiedData(String slug, int days) {
        return exportUnifiedData(slug, days, null);
    }

    @Override
    public List<UnifiedDataDTO> exportUnifiedData(String slug, int days, LocalDateTime endOverride) {
        FloodPoint point = floodPointRepository.findBySlug(slug)
                .orElseThrow(() -> new com.projeto.mapi.exception.ResourceNotFoundException("Ponto não encontrado: " + slug));

        // Define o intervalo de busca
        LocalDateTime end = (endOverride != null ? endOverride : LocalDateTime.now()).withMinute(0).withSecond(0).withNano(0);
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
        
        // Clima: vem da grade horária determinística do pipeline novo (observation_hourly_v), rótulo = fim da hora
        // em UTC. Hora incompleta ou ausente fica sem valor aqui (NULL), sem constante inventada pela view.
        List<com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyCell> weatherCells = weatherCells(point, start, end);
        
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

        weatherCells.forEach(w -> {
            LocalDateTime hour = LocalDateTime.ofInstant(w.hourEndUtc(), java.time.ZoneOffset.UTC);
            UnifiedDataDTO.UnifiedDataDTOBuilder b = timeline.get(hour);
            if (b == null || w.value() == null) return;
            switch (w.variable()) {
                case "precipitation" -> b.weatherPrecipitation(w.value());
                case "temperature" -> b.weatherTemperature(w.value());
                case "pressure" -> b.weatherPressure(w.value());
                case "weather_code" -> b.weatherCode((int) Math.round(w.value()));
                default -> { }
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

        // Sem imputação: ausência fica null (CSV: célula vazia). Rótulo ausente = não alagou (ver MISSING_POLICY).
        return result;
    }


    private List<com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyCell> weatherCells(
            FloodPoint point, LocalDateTime start, LocalDateTime end) {
        return observationRead.hourly(com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter.SOURCE,
                com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter.stationId(point.getLatitude(), point.getLongitude()),
                start.toInstant(java.time.ZoneOffset.UTC), end.toInstant(java.time.ZoneOffset.UTC));
    }

    @Override
    public CsvExport exportCsv(String slug, int days, LocalDateTime endOverride) {
        boolean all = "all".equals(slug);
        LocalDateTime end = (endOverride != null ? endOverride : LocalDateTime.now()).withMinute(0).withSecond(0).withNano(0);
        List<FloodPoint> points = all ? floodPointRepository.findAll()
                : List.of(floodPointRepository.findBySlug(slug).orElseThrow(() ->
                        new com.projeto.mapi.exception.ResourceNotFoundException("Ponto não encontrado: " + slug)));
        List<UnifiedDataDTO> data = new ArrayList<>();
        for (FloodPoint p : points) data.addAll(exportUnifiedDataWithAccumulated(p.getSlug(), days, end));
        String csv = generateCsv(data);

        LocalDateTime start = days > 0 ? end.minusDays(days) : LocalDateTime.of(2021, 1, 1, 0, 0);
        Map<String, java.util.Set<java.util.UUID>> runs = new TreeMap<>();
        Map<String, Integer> revisions = new TreeMap<>();
        for (FloodPoint p : points) {
            for (var cell : weatherCells(p, start, end)) {
                String key = com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter.SOURCE + "|" + cell.variable();
                runs.computeIfAbsent(key, k -> new java.util.TreeSet<>()).addAll(cell.runIds());
                revisions.merge(key, cell.maxRevision(), Math::max);
            }
        }
        List<com.projeto.mapi.dto.ExportManifestDTO.SourceProvenance> prov = runs.entrySet().stream()
                .map(e -> new com.projeto.mapi.dto.ExportManifestDTO.SourceProvenance(e.getKey().split("\\|")[0],
                        e.getKey().split("\\|")[1], List.copyOf(e.getValue()), revisions.get(e.getKey()))).toList();
        int flagged = (int) data.stream().filter(d -> d.getWeatherPrecipitation() == null).count();
        var manifest = new com.projeto.mapi.dto.ExportManifestDTO(
                com.projeto.mapi.service.ingestion.Hashing.sha256(csv), slug,
                start.toInstant(java.time.ZoneOffset.UTC), end.toInstant(java.time.ZoneOffset.UTC), "UTC",
                ingestionProps.getRuleVersion(), ingestionProps.getCodeVersion(), data.size(), flagged, prov,
                MISSING_POLICY, missingCounts(data),
                List.of("sensores (ANA, APAC, Cemaden): ainda lidos de sensor_data (legado, fuso não verificado)",
                        "maré: TideService", "rótulo: flood_events (CKAN ainda não migrado)"));
        return new CsvExport(csv, manifest);
    }

    private static Map<String, Integer> missingCounts(List<UnifiedDataDTO> data) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (String c : MISSING_COLUMNS) out.put(c, 0);
        for (UnifiedDataDTO d : data) {
            Object[] v = missingValues(d);
            for (int i = 0; i < v.length; i++) if (v[i] == null) out.merge(MISSING_COLUMNS[i], 1, Integer::sum);
        }
        return out;
    }

    @Override
    public List<UnifiedDataDTO> exportUnifiedDataWithAccumulated(String slug, int days, LocalDateTime endOverride) {
        List<UnifiedDataDTO> baseData = exportUnifiedData(slug, days, endOverride);
        return addAccumulated(baseData);
    }

    @Override
    public List<UnifiedDataDTO> exportUnifiedDataWithAccumulated(String slug, int days) {
        return exportUnifiedDataWithAccumulated(slug, days, null);
    }

    private List<UnifiedDataDTO> addAccumulated(List<UnifiedDataDTO> baseData) {
        for (int i = 0; i < baseData.size(); i++) {
            baseData.get(i).setAccumulated3h(calculateSum(baseData, i, 3));
            baseData.get(i).setAccumulated6h(calculateSum(baseData, i, 6));
            baseData.get(i).setAccumulated12h(calculateSum(baseData, i, 12));
            baseData.get(i).setAccumulated24h(calculateSum(baseData, i, 24));
            baseData.get(i).setAccumulated48h(calculateSum(baseData, i, 48));
        }
        
        return baseData;
    }

    /** Soma das últimas {@code hours} horas; null se a janela não tem todas as horas ou alguma hora está sem valor. */
    private Double calculateSum(List<UnifiedDataDTO> data, int index, int hours) {
        if (index + 1 < hours) return null;
        double sum = 0.0;
        for (int i = index; i > index - hours; i--) {
            Double val = data.get(i).getWeatherPrecipitation();
            if (val == null) return null;
            sum += val;
        }
        return Math.round(sum * 100.0) / 100.0;
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

    /** Política de ausência declarada no manifesto (ADR 0006: lacuna = NULL, nunca constante). */
    public static final String MISSING_POLICY = "Sem imputação. Valor ausente = célula vazia no CSV (null no JSON). "
            + "Colunas <var>_missing (1 = ausente, 0 = presente) ao fim do cabeçalho. acumuladosNh = vazio se a janela "
            + "não tem N horas ou alguma hora sem precipitação. isFlooded ausente = false (sem ocorrência registrada "
            + "no rótulo, não imputação de feature).";

    /** Colunas de valor que podem faltar, na ordem em que os sinalizadores _missing aparecem no cabeçalho. */
    private static final String[] MISSING_COLUMNS = {"sensorPrecipitation", "sensorWaterLevel", "sensorSoilHumidity",
            "weatherPrecipitation", "weatherTemperature", "weatherPressure", "weatherCode", "tideHeight",
            "accumulated3h", "accumulated6h", "accumulated12h", "accumulated24h", "accumulated48h"};

    private static Object[] missingValues(UnifiedDataDTO d) {
        return new Object[]{d.getSensorPrecipitation(), d.getSensorWaterLevel(), d.getSensorSoilHumidity(),
                d.getWeatherPrecipitation(), d.getWeatherTemperature(), d.getWeatherPressure(), d.getWeatherCode(),
                d.getTideHeight(), d.getAccumulated3h(), d.getAccumulated6h(), d.getAccumulated12h(),
                d.getAccumulated24h(), d.getAccumulated48h()};
    }

    private static String cell(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    @Override
    public String generateCsv(List<UnifiedDataDTO> data) {
        StringBuilder csv = new StringBuilder();
        csv.append("floodPointSlug,timestamp,sensorPrecipitation,sensorWaterLevel,sensorSoilHumidity,weatherPrecipitation,weatherTemperature,weatherPressure,weatherCode,tideHeight,accumulated3h,accumulated6h,accumulated12h,accumulated24h,accumulated48h,isFlooded,severity");
        for (String c : MISSING_COLUMNS) csv.append(',').append(c).append("_missing");
        csv.append('\n');

        for (UnifiedDataDTO d : data) {
            csv.append(String.join(",",
                    cell(d.getFloodPointSlug()), cell(d.getTimestamp()),
                    cell(d.getSensorPrecipitation()), cell(d.getSensorWaterLevel()), cell(d.getSensorSoilHumidity()),
                    cell(d.getWeatherPrecipitation()), cell(d.getWeatherTemperature()), cell(d.getWeatherPressure()),
                    cell(d.getWeatherCode()), cell(d.getTideHeight()), cell(d.getAccumulated3h()),
                    cell(d.getAccumulated6h()), cell(d.getAccumulated12h()), cell(d.getAccumulated24h()),
                    cell(d.getAccumulated48h()), cell(d.getIsFlooded()), cell(d.getSeverity())));
            for (Object v : missingValues(d)) csv.append(',').append(v == null ? '1' : '0');
            csv.append('\n');
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

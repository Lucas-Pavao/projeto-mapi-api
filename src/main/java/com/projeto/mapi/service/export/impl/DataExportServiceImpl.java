package com.projeto.mapi.service.export.impl;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.model.*;
import com.projeto.mapi.repository.*;
import com.projeto.mapi.service.export.DataExportService;
import com.projeto.mapi.service.export.TideFeatureCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import com.projeto.mapi.service.export.label.OccurrenceLabeler;
import com.projeto.mapi.service.export.label.OccurrenceLabeler.Occ;

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
    private final com.projeto.mapi.service.terrain.TerrainExportColumns terrainColumns;
    private final com.projeto.mapi.repository.ingestion.OccurrenceRepository occurrenceRepository;
    private final com.projeto.mapi.config.ExportLabelProperties labelProps;

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
        LocalDateTime end = (endOverride != null ? endOverride : LocalDateTime.now(java.time.ZoneOffset.UTC)).withMinute(0).withSecond(0).withNano(0);
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
        
        List<com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyCell> soilCells = soilCells(point, start, end);

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

        soilCells.forEach(w -> {
            UnifiedDataDTO.UnifiedDataDTOBuilder b = timeline.get(LocalDateTime.ofInstant(w.hourEndUtc(), java.time.ZoneOffset.UTC));
            if (b != null && w.value() != null) b.soilMoisture07cm(w.value());
        });

        // 5. Processar e Refinar (Tide + Flood Labels)
        // Maré prevista do pipeline (TIDE_PE02_HOURLY + extremos): uma vez por chamada; sem fallback para outra fonte.
        java.time.Instant tideFrom = start.toInstant(java.time.ZoneOffset.UTC);
        java.time.Instant tideTo = end.toInstant(java.time.ZoneOffset.UTC);
        Map<java.time.Instant, Double> tideHourly = tideHourly(tideFrom.minusSeconds(3600), tideTo.plusSeconds(6 * 3600));
        List<TideFeatureCalculator.Extreme> tideExtremes = observationRead
                .tideExtremes(tideFrom.minusSeconds(2 * 86400L), tideTo.plusSeconds(3 * 86400L)).stream()
                .map(e -> new TideFeatureCalculator.Extreme(e.tsUtc(), e.level(), e.qualifier())).toList();
        var tideIndex = TideFeatureCalculator.index(tideExtremes); // índice uma vez por chamada (F2-BACK-22)

        var linkage = labelLinkage();
        List<Occ> occs = linkage.map(l -> occurrencesOf(point, l)).orElse(List.of());

        List<UnifiedDataDTO> result = timeline.entrySet().stream().map(entry -> {
            LocalDateTime ts = entry.getKey();
            UnifiedDataDTO.UnifiedDataDTOBuilder builder = entry.getValue();

            // Maré prevista (altura em t, t+1..t+6, máximo, preamar seguinte, amplitude do dia, derivada)
            var tf = TideFeatureCalculator.at(ts.toInstant(java.time.ZoneOffset.UTC), tideHourly, tideIndex);
            builder.tideHeight(tf.height()).tideHeightAhead(tf.ahead()).tideMaxNext6h(tf.maxNext6h())
                    .tideHoursToNextHigh(tf.hoursToNextHigh()).tideRangeDayM(tf.rangeDayM())
                    .tideRising(tf.rising()).tideSlopeMh(tf.slopeMh());

            // Rótulo novo (pipeline): t = fim da hora em UTC; só preenche se há linkage_config vigente
            if (linkage.isPresent()) {
                var l = OccurrenceLabeler.label(occs, ts.toInstant(java.time.ZoneOffset.UTC),
                        labelProps.getHorizonsHours(), labelProps::postEventHours);
                builder.labelByHorizon(l.byHorizon()).labelDay(l.day())
                        .labelExcludedReason(l.excludedReason()).occurrenceIds(l.occurrenceIds());
            }

            // Label LEGADO de Alagamento (flood_events; obsoleto, mantido por compatibilidade)
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


    /** linkage_config vigente do rótulo: o configurado por id ou o de menor id com o raio de {@code mapi.export.label.linkage-radius-m} (500 m). */
    private java.util.Optional<com.projeto.mapi.repository.ingestion.OccurrenceRepository.LinkageRef> labelLinkage() {
        return occurrenceRepository.occurrenceLinkage(labelProps.getLinkageConfigId(), labelProps.getLinkageRadiusM());
    }

    private List<Occ> occurrencesOf(FloodPoint point,
            com.projeto.mapi.repository.ingestion.OccurrenceRepository.LinkageRef linkage) {
        return occurrenceRepository.matchedOccurrences(point.getId(), linkage.id()).stream()
                .map(o -> new Occ(o.id(), o.from(), o.to(), o.hour())).toList();
    }

    private List<com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyCell> weatherCells(
            FloodPoint point, LocalDateTime start, LocalDateTime end) {
        return observationRead.hourly(com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter.SOURCE,
                com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter.stationId(point.getLatitude(), point.getLongitude()),
                start.toInstant(java.time.ZoneOffset.UTC), end.toInstant(java.time.ZoneOffset.UTC));
    }

    /** Altura da maré prevista nas horas cheias UTC (grade horária do pipeline; hora sem valor = ausente). */
    private Map<java.time.Instant, Double> tideHourly(java.time.Instant from, java.time.Instant to) {
        Map<java.time.Instant, Double> m = new java.util.HashMap<>();
        for (var c : observationRead.hourly(TIDE_SOURCE, TIDE_STATION, from, to)) {
            if (c.value() != null && TIDE_VARIABLE.equals(c.variable())) m.put(c.hourEndUtc(), c.value());
        }
        return m;
    }

    private static final String TIDE_SOURCE = com.projeto.mapi.service.ingestion.adapter.tide.TideHourlyAdapter.SOURCE;
    private static final String TIDE_STATION = com.projeto.mapi.service.ingestion.adapter.tide.TideConstants.STATION;
    private static final String TIDE_VARIABLE = com.projeto.mapi.service.ingestion.adapter.tide.TideConstants.VARIABLE_HOURLY;

    /** Umidade do solo ERA5-Land (F2-BACK-15); mesma estação (coordenada do ponto) da fonte Open-Meteo archive. */
    private List<com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyCell> soilCells(
            FloodPoint point, LocalDateTime start, LocalDateTime end) {
        return observationRead.hourly(com.projeto.mapi.service.ingestion.adapter.Era5LandAdapter.SOURCE,
                com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter.stationId(point.getLatitude(), point.getLongitude()),
                start.toInstant(java.time.ZoneOffset.UTC), end.toInstant(java.time.ZoneOffset.UTC));
    }

    @Override
    public CsvExport exportCsv(String slug, int days, LocalDateTime endOverride) {
        boolean all = "all".equals(slug);
        LocalDateTime end = (endOverride != null ? endOverride : LocalDateTime.now(java.time.ZoneOffset.UTC)).withMinute(0).withSecond(0).withNano(0);
        List<FloodPoint> points = all ? floodPointRepository.findAll()
                : List.of(floodPointRepository.findBySlug(slug).orElseThrow(() ->
                        new com.projeto.mapi.exception.ResourceNotFoundException("Ponto não encontrado: " + slug)));
        List<UnifiedDataDTO> data = new ArrayList<>();
        for (FloodPoint p : points) data.addAll(exportUnifiedDataWithAccumulated(p.getSlug(), days, end));
        var terrain = terrainColumns.append(generateCsv(data)); // ADR 0008: colunas terrain_*
        String csv = terrain.csv();

        LocalDateTime start = days > 0 ? end.minusDays(days) : LocalDateTime.of(2021, 1, 1, 0, 0);
        Map<String, java.util.Set<java.util.UUID>> runs = new TreeMap<>();
        Map<String, Integer> revisions = new TreeMap<>();
        for (FloodPoint p : points) {
            for (var cell : weatherCells(p, start, end)) {
                String key = com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter.SOURCE + "|" + cell.variable();
                runs.computeIfAbsent(key, k -> new java.util.TreeSet<>()).addAll(cell.runIds());
                revisions.merge(key, cell.maxRevision(), Math::max);
            }
            for (var cell : soilCells(p, start, end)) {
                String key = com.projeto.mapi.service.ingestion.adapter.Era5LandAdapter.SOURCE + "|" + cell.variable();
                runs.computeIfAbsent(key, k -> new java.util.TreeSet<>()).addAll(cell.runIds());
                revisions.merge(key, cell.maxRevision(), Math::max);
            }
        }
        for (var cell : observationRead.hourly(TIDE_SOURCE, TIDE_STATION, start.toInstant(java.time.ZoneOffset.UTC),
                end.toInstant(java.time.ZoneOffset.UTC))) {
            String key = TIDE_SOURCE + "|" + cell.variable();
            runs.computeIfAbsent(key, k -> new java.util.TreeSet<>()).addAll(cell.runIds());
            revisions.merge(key, cell.maxRevision(), Math::max);
        }
        List<com.projeto.mapi.dto.ExportManifestDTO.SourceProvenance> prov = runs.entrySet().stream()
                .map(e -> new com.projeto.mapi.dto.ExportManifestDTO.SourceProvenance(e.getKey().split("\\|")[0],
                        e.getKey().split("\\|")[1], List.copyOf(e.getValue()), revisions.get(e.getKey()))).toList();
        int flagged = (int) data.stream().filter(d -> d.getWeatherPrecipitation() == null).count();
        var manifest = new com.projeto.mapi.dto.ExportManifestDTO(
                com.projeto.mapi.service.ingestion.Hashing.sha256(csv), slug,
                start.toInstant(java.time.ZoneOffset.UTC), end.toInstant(java.time.ZoneOffset.UTC), "UTC",
                ingestionProps.getRuleVersion(), ingestionProps.getCodeVersion(), data.size(), flagged, prov, terrain.provenance(),
                MISSING_POLICY, missingCounts(data),
                List.of("sensores (ANA, APAC, Cemaden): ainda lidos de sensor_data (legado, fuso não verificado)",
                        "maré: pipeline (TIDE_PE02_HOURLY a partir de TABUAMARE_PE02/DHN_TABUA_PE02), alturas em NR_DHN_pe02",
                        "rótulo legado isFlooded/severity: flood_events (obsoleto); rótulo novo: flood_occurrence"),
                List.of("isFlooded", "severity"), labelManifest(points, data, start, end),
                new com.projeto.mapi.dto.ExportManifestDTO.TideManifest(
                        com.projeto.mapi.service.ingestion.adapter.tide.TideConstants.VERTICAL_REFERENCE,
                        "COSENO entre extremos consecutivos da tábua DHN (Porto do Recife, pe02), hora cheia UTC; "
                                + "sem extrapolar além do primeiro/último extremo nem entre extremos a mais de 8 h",
                        "[sem fonte] não medida contra marégrafo; resolução da tábua 0,1 m (2021-2024) e 0,01 m (2025-2026)",
                        TIDE_NOTE, (int) data.stream().filter(d -> d.getTideHeight() != null).count()));
        return new CsvExport(csv, manifest);
    }

    static final String LABEL_POLICY = "Rótulo de flood_occurrence + occurrence_point_match (ADR 0006), não de flood_events. "
            + "t = fim da hora, UTC. label_h<H> = 1 se há ocorrência HOUR casada com o ponto em (t, t+H]; 0 se não; vazio "
            + "se a hora está excluída: DAY_EVENT_SAME_DAY (a janela (t, t+H] toca um intervalo DAY do ponto; nunca vira "
            + "negativo) ou POST_EVENT_WINDOW (ocorrência em (t-G, t]). label_day = 1 se há ocorrência HOUR ou DAY no dia "
            + "civil America/Recife de t. label_excluded_reason refere-se ao maior horizonte. occurrence_ids = ocorrências "
            + "que justificam o rótulo (separadas por ';'). Os eventos (agrupamento por G, MODELAGEM-IA A.3.2) não são "
            + "calculados aqui; o tamanho efetivo da amostra é o número de eventos, não de horas. O rótulo é alvo, nunca "
            + "feature. Sem linkage_config vigente as colunas label_* saem vazias.";

    private com.projeto.mapi.dto.ExportManifestDTO.LabelManifest labelManifest(List<FloodPoint> points,
            List<UnifiedDataDTO> data, LocalDateTime start, LocalDateTime end) {
        var linkage = labelLinkage();
        Map<String, com.projeto.mapi.dto.ExportManifestDTO.LabelCounts> counts = new java.util.LinkedHashMap<>();
        Map<Integer, Integer> g = new java.util.LinkedHashMap<>();
        for (int h : labelProps.getHorizonsHours()) {
            int pos = 0, neg = 0, exc = 0;
            for (UnifiedDataDTO d : data) {
                if (d.getLabelByHorizon() == null) continue;
                Integer v = d.getLabelByHorizon().get(h);
                if (v == null) exc++; else if (v == 1) pos++; else neg++;
            }
            counts.put("label_h" + h, new com.projeto.mapi.dto.ExportManifestDTO.LabelCounts(pos, neg, exc));
            g.put(h, labelProps.postEventHours(h));
        }
        Map<String, Integer> byReason = new TreeMap<>();
        int dayPos = 0;
        for (UnifiedDataDTO d : data) {
            if (d.getLabelExcludedReason() != null) byReason.merge(d.getLabelExcludedReason(), 1, Integer::sum);
            if (Integer.valueOf(1).equals(d.getLabelDay())) dayPos++;
        }
        java.time.Instant from = start.toInstant(java.time.ZoneOffset.UTC), to = end.toInstant(java.time.ZoneOffset.UTC);
        int hour = 0, day = 0;
        if (linkage.isPresent()) {
            for (FloodPoint p : points) {
                for (Occ o : occurrencesOf(p, linkage.get())) {
                    if (o.from().isAfter(to) || !o.to().isAfter(from)) continue;
                    if (o.hour()) hour++; else day++;
                }
            }
        }
        return new com.projeto.mapi.dto.ExportManifestDTO.LabelManifest(LABEL_POLICY, labelProps.getHorizonsHours(), g,
                linkage.map(l -> l.id()).orElse(null), linkage.map(l -> l.radiusM()).orElse(null),
                linkage.map(l -> l.method()).orElse(null), counts, byReason, dayPos, hour, day);
    }

    private static Map<String, Integer> missingCounts(List<UnifiedDataDTO> data) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (String c : MISSING_COLUMNS) out.put(c, 0);
        out.put("soil_moisture_0_7cm", 0);
        for (String c : TIDE_COLUMNS) out.put(c, 0);
        for (UnifiedDataDTO d : data) {
            Object[] tv = tideValues(d);
            for (int i = 0; i < tv.length; i++) if (tv[i] == null) out.merge(TIDE_COLUMNS[i], 1, Integer::sum);
            Object[] v = missingValues(d);
            for (int i = 0; i < v.length; i++) if (v[i] == null) out.merge(MISSING_COLUMNS[i], 1, Integer::sum);
            if (d.getSoilMoisture07cm() == null) out.merge("soil_moisture_0_7cm", 1, Integer::sum);
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
                .timestampUtc(ts.toInstant(java.time.ZoneOffset.UTC))
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

    static final String TIDE_NOTE = "Altura da tábua referida ao NR da DHN; NÃO convertida ao datum do MDT (ADR 0009). "
            + "A maré prevista em t+1..t+6 é exceção declarada de 'futuro' (tábua publicada antes; MODELAGEM-IA A.2.2). "
            + "tide_rising = 1 se a derivada central (h(t+1)-h(t-1))/2 > 0, 0 se < 0; se nula (hora de preamar/baixa-mar), desempata pelo passo h(t+1)-h(t) (>0 = 1, <0 = 0); vazio (com tide_rising_missing = 1) só se o insumo faltar ou o passo também for nulo; "
            + "tide_range_day_m = máximo - mínimo dos extremos do dia civil America/Recife de t (proxy de sizígia/quadratura).";

    /** Política de ausência declarada no manifesto (ADR 0006: lacuna = NULL, nunca constante). */
    public static final String MISSING_POLICY = "Sem imputação. Valor ausente = célula vazia no CSV (null no JSON). "
            + "Colunas <var>_missing (1 = ausente, 0 = presente) ao fim do cabeçalho. acumuladosNh = vazio se a janela "
            + "não tem N horas ou alguma hora sem precipitação. isFlooded ausente = false (sem ocorrência registrada "
            + "no rótulo, não imputação de feature).";

    /** Colunas de valor que podem faltar, na ordem em que os sinalizadores _missing aparecem no cabeçalho. */
    private static final String[] MISSING_COLUMNS = {"sensorPrecipitation", "sensorWaterLevel", "sensorSoilHumidity",
            "weatherPrecipitation", "weatherTemperature", "weatherPressure", "weatherCode", "tideHeight",
            "accumulated3h", "accumulated6h", "accumulated12h", "accumulated24h", "accumulated48h"};

    /** Colunas de maré prevista acrescentadas ao fim do CSV (valores, depois os sinalizadores _missing na mesma ordem). */
    static final String[] TIDE_COLUMNS = {"tide_height_t1", "tide_height_t2", "tide_height_t3", "tide_height_t4",
            "tide_height_t5", "tide_height_t6", "tide_max_next_6h", "tide_hours_to_next_high", "tide_range_day_m",
            "tide_rising", "tide_slope_m_h"};

    private static Object[] tideValues(UnifiedDataDTO d) {
        java.util.Map<Integer, Double> a = d.getTideHeightAhead();
        Object[] out = new Object[TIDE_COLUMNS.length];
        for (int k = 1; k <= TideFeatureCalculator.HORIZON_HOURS; k++) out[k - 1] = a == null ? null : a.get(k);
        out[6] = d.getTideMaxNext6h();
        out[7] = d.getTideHoursToNextHigh();
        out[8] = d.getTideRangeDayM();
        out[9] = d.getTideRising();
        out[10] = d.getTideSlopeMh();
        return out;
    }

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
        csv.append("floodPointSlug,timestamp,sensorPrecipitation,sensorWaterLevel,sensorSoilHumidity,weatherPrecipitation,weatherTemperature,weatherPressure,weatherCode,tideHeight,accumulated3h,accumulated6h,accumulated12h,accumulated24h,accumulated48h,isFlooded,severity,timestampUtc");
        for (String c : MISSING_COLUMNS) csv.append(',').append(c).append("_missing");
        for (int h : labelProps.getHorizonsHours()) csv.append(",label_h").append(h);
        csv.append(",label_day,label_excluded_reason,occurrence_ids,soil_moisture_0_7cm,soil_moisture_0_7cm_missing");
        // Maré prevista (F2-BACK-14): colunas novas no FIM, cada uma com sua _missing; tideHeight (acima) é a altura em t.
        for (String c : TIDE_COLUMNS) csv.append(',').append(c);
        for (String c : TIDE_COLUMNS) csv.append(',').append(c).append("_missing");
        csv.append('\n');

        for (UnifiedDataDTO d : data) {
            csv.append(String.join(",",
                    cell(d.getFloodPointSlug()), cell(d.getTimestamp()),
                    cell(d.getSensorPrecipitation()), cell(d.getSensorWaterLevel()), cell(d.getSensorSoilHumidity()),
                    cell(d.getWeatherPrecipitation()), cell(d.getWeatherTemperature()), cell(d.getWeatherPressure()),
                    cell(d.getWeatherCode()), cell(d.getTideHeight()), cell(d.getAccumulated3h()),
                    cell(d.getAccumulated6h()), cell(d.getAccumulated12h()), cell(d.getAccumulated24h()),
                    cell(d.getAccumulated48h()), cell(d.getIsFlooded()), cell(d.getSeverity()), cell(d.getTimestampUtc())));
            for (Object v : missingValues(d)) csv.append(',').append(v == null ? '1' : '0');
            for (int h : labelProps.getHorizonsHours())
                csv.append(',').append(d.getLabelByHorizon() == null ? "" : cell(d.getLabelByHorizon().get(h)));
            csv.append(',').append(cell(d.getLabelDay())).append(',').append(cell(d.getLabelExcludedReason()))
                    .append(',').append(cell(d.getOccurrenceIds()))
                    .append(',').append(cell(d.getSoilMoisture07cm()))
                    .append(',').append(d.getSoilMoisture07cm() == null ? '1' : '0');
            Object[] tide = tideValues(d);
            for (Object v : tide) csv.append(',').append(cell(v));
            for (Object v : tide) csv.append(',').append(v == null ? '1' : '0');
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

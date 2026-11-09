package com.projeto.mapi.service.analise.impl;

import com.projeto.mapi.config.ExportLabelProperties;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.ExportManifestDTO;
import com.projeto.mapi.dto.RainEventDTO;
import com.projeto.mapi.dto.RainEventManifestDTO;
import com.projeto.mapi.dto.RainEventSummaryDTO;
import com.projeto.mapi.dto.RainEventSummaryTableDTO;
import com.projeto.mapi.dto.RainEventTableDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.analise.RainEventReadRepository;
import com.projeto.mapi.repository.analise.RainEventReadRepository.HourRow;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.repository.ingestion.OccurrenceRepository;
import com.projeto.mapi.repository.terrain.TerrainRepository;
import com.projeto.mapi.service.analise.RainEventClassifier;
import com.projeto.mapi.service.analise.RainEventCsv;
import com.projeto.mapi.service.analise.RainEventDetector;
import com.projeto.mapi.service.analise.RainEventDetector.DetectedEvent;
import com.projeto.mapi.service.analise.RainEventDetector.HourSample;
import com.projeto.mapi.service.analise.RainEventParams;
import com.projeto.mapi.service.analise.RainEventService;
import com.projeto.mapi.service.analise.RainEventSummarizer;
import com.projeto.mapi.service.export.TideFeatureCalculator;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.adapter.Era5LandAdapter;
import com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveAdapter;
import com.projeto.mapi.service.ingestion.adapter.tide.TideConstants;
import com.projeto.mapi.service.ingestion.adapter.tide.TideHourlyAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Tabela de eventos de chuva e resumo por tipo (ADR 0017). Lê a grade horária do pipeline (ADR 0006); não coleta,
 * não grava e não imputa. A detecção é {@link RainEventDetector} (pura); aqui só se busca o dado e se junta o contexto.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RainEventServiceImpl implements RainEventService {

    static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final Duration HOUR = Duration.ofHours(1);
    private static final int ANTECEDENT_MAX_H = 72;
    /** Janela máxima aceita (7 anos), para limitar o custo da consulta. */
    private static final Duration MAX_WINDOW = Duration.ofDays(7 * 366);

    static final String CIRCULARITY_WARNING = "ckanOccurrenceSameDay é coluna de VALIDAÇÃO. Não calibrar o limiar de "
            + "classificação, a capacidade, o intervalo seco nem o mínimo do evento olhando essa coluna: o resultado vira "
            + "circular (mesmo defeito que levou a desativar o realinhamento do rótulo ao pico de chuva, ADR 0006).";

    static final List<String> RULES = List.of(
            "Hora rotulada pelo fim do intervalo (t-1h, t], UTC; America/Recife só nas colunas *Local, month e season.",
            "Hora molhada = valor > 0; hora seca = 0 medido; hora incompleta ou sem leitura = lacuna (nunca zero). ANA: a "
                    + "hora exige as 4 leituras de 15 min e descarta a leitura logo depois de lacuna (V15).",
            "Evento = horas molhadas separadas por menos de dryGapHours horas não molhadas; termina na última hora molhada "
                    + "antes de dryGapHours horas não molhadas seguidas.",
            "Lacuna dentro do evento ou nas dryGapHours horas antes/depois marca hasGap; nada é imputado.",
            "Evento com totalMm < minEventMm é descartado como ruído; as horas dele contam no intervalo entre vizinhos.",
            "dryHoursBefore = horas entre o fim do evento retido anterior e o início deste; nulo sem anterior na série "
                    + "consultada ou com lacuna entre os dois.",
            "truncated = começa antes de from, termina depois de to ou encosta na borda da série consultada; truncado "
                    + "fica na tabela e sai do resumo.",
            "type pelo pico horário (maxHourlyMmH) e pela regra de params.classification com os limites "
                    + "params.typeBoundsMmH.",
            "excessOverCapacityMm = soma, nas horas do evento, de max(0, chuva horária - capacityMmH); hoursAboveCapacity "
                    + "= horas com chuva horária > capacityMmH. Capacidade em chuva equivalente (mm/h), não vazão.",
            "estimatedInflowMm = totalMm × runoffCoefficient.",
            "Maré: TIDE_PE02_HOURLY (prevista, NR DHN pe02) nas horas cheias do início ao fim do evento; tideRangeDayM = "
                    + "máx - mín dos extremos do dia local do início; tideRegime = SIZIGIA se tideRangeDayM >= "
                    + "springRangeM, senão QUADRATURA.",
            "soilMoistureBefore = ERA5-Land 0-7 cm na hora que termina no início do evento; antecedentRainNh = soma das N "
                    + "horas que terminam no início, da mesma fonte e estação da chuva; nulo se faltar alguma hora.",
            "Rio: estação ANA_SOAP com water_level mais próxima do ponto; riverLevelMaxM = maior leitura com carimbo em "
                    + "(startUtc, endUtc].",
            "ckanOccurrenceSameDay = ocorrência CKAN casada com o ponto (linkage_config do export) em algum dia local "
                    + "do evento; nulo sem linkage_config.",
            "Vínculo (ADR 0018): pluviômetro mais próximo dentro de sensorLinkageRadiusM (linkage_config SENSOR_POINT); "
                    + "fora do raio a linha usa a reanálise e rainOrigin = REANALYSIS_FALLBACK; source é a fonte efetiva.",
            "eventId = 16 primeiros hex do SHA-256 de slug|startUtc|fonte efetiva|regras|raio do vínculo.");

    private final FloodPointRepository floodPointRepository;
    private final RainEventReadRepository repo;
    private final ObservationReadRepository observationRead;
    private final OccurrenceRepository occurrenceRepository;
    private final TerrainRepository terrainRepository;
    private final ExportLabelProperties labelProps;
    private final IngestionProperties ingestionProps;

    /** Resultado intermediário de uma extração (eventos + o que o manifesto e o resumo precisam). */
    private record Extraction(List<String> slugs, List<RainEventDTO> events,
                              Map<String, RainEventSummarizer.Coverage> coverage,
                              List<RainEventManifestDTO.StationUsed> stations,
                              Map<String, Set<UUID>> runs, Map<String, Integer> revisions,
                              Long terrainRunId, double sensorRadiusM, Long sensorLinkageId, Long linkageId,
                              String sourceTz, String sourceVerification) {}

    @Override
    public EventsResult events(RainEventParams p) {
        Extraction x = extract(p);
        String csv = RainEventCsv.write(RainEventDTO.class, x.events());
        RainEventManifestDTO manifest = manifest(p, x, "RAIN_EVENTS", Hashing.sha256(csv), x.events().size());
        return new EventsResult(new RainEventTableDTO(manifest, x.events()), csv);
    }

    @Override
    public SummaryResult summary(RainEventParams p) {
        Extraction x = extract(p);
        List<RainEventSummaryDTO> rows = RainEventSummarizer.summarizeAll(x.slugs(), x.events(), x.coverage());
        String csv = RainEventCsv.write(RainEventSummaryDTO.class, rows);
        RainEventManifestDTO manifest = manifest(p, x, "RAIN_EVENT_SUMMARY", Hashing.sha256(csv), rows.size());
        return new SummaryResult(new RainEventSummaryTableDTO(manifest, rows), csv);
    }

    // ------------------------------------------------------------------------------------------------ extração

    private Extraction extract(RainEventParams p) {
        validate(p);
        List<FloodPoint> points = points(p.slug());
        Instant windowFirstHourEnd = p.from().truncatedTo(ChronoUnit.HOURS).plus(HOUR);
        Instant windowLastHourEnd = ceilHour(p.to());
        Instant seriesFirst = windowFirstHourEnd.minus(Duration.ofHours(ANTECEDENT_MAX_H + p.dryGapHours()));
        Instant seriesLast = windowLastHourEnd.plus(Duration.ofHours(p.dryGapHours()));

        var tz = repo.sourceTz(p.source().pipelineSource);
        double radiusM = ingestionProps.getSensorLinkageRadiusM();
        Long sensorLinkageId = repo.sensorLinkage(radiusM).map(RainEventReadRepository.SensorLinkage::id).orElse(null);
        Map<String, Set<UUID>> runs = new TreeMap<>();
        Map<String, Integer> revisions = new TreeMap<>();

        // contexto comum a todos os pontos: maré
        List<HourRow> tideRows = repo.hourly(TideHourlyAdapter.SOURCE, TideConstants.STATION,
                TideConstants.VARIABLE_HOURLY, seriesFirst, seriesLast);
        Map<Instant, Double> tide = values(tideRows);
        var tideIndex = TideFeatureCalculator.index(observationRead
                .tideExtremes(seriesFirst.minus(Duration.ofDays(2)), seriesLast.plus(Duration.ofDays(2))).stream()
                .map(e -> new TideFeatureCalculator.Extreme(e.tsUtc(), e.level(), e.qualifier())).toList());
        var linkage = occurrenceRepository.occurrenceLinkage(labelProps.getLinkageConfigId(),
                labelProps.getLinkageRadiusM());
        Long terrainRunId = terrainRepository.currentRun().map(TerrainRepository.RunRow::id).orElse(null);

        List<String> slugs = new ArrayList<>();
        List<RainEventDTO> events = new ArrayList<>();
        Map<String, RainEventSummarizer.Coverage> coverage = new LinkedHashMap<>();
        List<RainEventManifestDTO.StationUsed> stations = new ArrayList<>();

        for (FloodPoint pt : points) {
            String slug = pt.getSlug();
            slugs.add(slug);
            Optional<RainStation> station = rainStation(p, pt, radiusM);
            stations.add(new RainEventManifestDTO.StationUsed(slug, "rain:" + station.map(RainStation::origin).orElse("NONE"),
                    station.map(RainStation::source).orElse(p.source().pipelineSource),
                    station.map(RainStation::id).orElse(null), station.map(RainStation::distanceM).orElse(null)));
            var river = repo.nearestStation(RIVER_SOURCE, "water_level", null, pt.getLatitude(), pt.getLongitude());
            stations.add(new RainEventManifestDTO.StationUsed(slug, "river", RIVER_SOURCE,
                    river.map(RainEventReadRepository.StationRow::stationId).orElse(null),
                    river.map(r -> round(r.distanceM(), 1)).orElse(null)));
            if (station.isEmpty()) {
                coverage.put(slug, new RainEventSummarizer.Coverage(hoursBetween(windowFirstHourEnd, windowLastHourEnd), 0));
                continue;
            }
            RainStation st = station.get();
            String stationId = st.id();

            List<HourRow> rainRows = repo.hourly(st.source(), stationId, "precipitation", seriesFirst, seriesLast);
            List<HourSample> series = grid(rainRows, seriesFirst, seriesLast, st.readingsPerHour());
            coverage.put(slug, coverageOf(series, windowFirstHourEnd, windowLastHourEnd));
            for (HourRow r : rainRows) {
                if (r.hourEndUtc().isBefore(windowFirstHourEnd) || r.hourEndUtc().isAfter(windowLastHourEnd)) continue;
                track(runs, revisions, st.source() + "|precipitation", r);
            }

            List<DetectedEvent> detected = RainEventDetector.detect(series,
                    new RainEventDetector.Rules(p.dryGapHours(), p.minEventMm(), p.capacityMmH()));
            detected = detected.stream()
                    .filter(e -> e.endUtc().isAfter(p.from()) && e.startUtc().isBefore(p.to())).toList();
            if (detected.isEmpty()) continue;

            // contexto do ponto
            Map<Instant, Double> sub = st.subHourly()
                    ? repo.subHourlyRain(st.source(), stationId, st.resolution(),
                    seriesFirst.minus(Duration.ofHours(2)), seriesLast)
                    : Map.of();
            String gridStation = OpenMeteoArchiveAdapter.stationId(pt.getLatitude(), pt.getLongitude());
            List<HourRow> soilRows = repo.hourly(Era5LandAdapter.SOURCE, gridStation, Era5LandAdapter.VARIABLE,
                    seriesFirst, seriesLast);
            Map<Instant, Double> soil = values(soilRows);
            java.util.NavigableMap<Instant, Double> riverLevel = river.map(r -> repo.readings(RIVER_SOURCE,
                    r.stationId(), "water_level", seriesFirst, seriesLast)).orElse(new TreeMap<>());
            Double alert = river.map(r -> parse(r.alertLevelM())).orElse(null);
            var terrain = terrainRepository.findCurrentPoint(slug).filter(t -> "OK".equals(t.status()));
            List<OccurrenceRepository.MatchedOccurrence> occs = linkage
                    .map(l -> occurrenceRepository.matchedOccurrences(pt.getId(), l.id())).orElse(null);
            Map<Instant, Integer> hourIndex = new HashMap<>();
            for (int k = 0; k < series.size(); k++) hourIndex.put(series.get(k).hourEnd(), k);

            for (DetectedEvent e : detected) {
                events.add(row(p, slug, st, radiusM, river.orElse(null), alert, e, series,
                        hourIndex, sub, tide, tideIndex, soil, riverLevel, terrain.orElse(null), occs));
                // proveniência do contexto usado pelo evento
                for (Instant h = e.startUtc(); !h.isAfter(e.endUtc()); h = h.plus(HOUR)) {
                    trackAt(runs, revisions, TideHourlyAdapter.SOURCE + "|" + TideConstants.VARIABLE_HOURLY, tideRows, h);
                }
                trackAt(runs, revisions, Era5LandAdapter.SOURCE + "|" + Era5LandAdapter.VARIABLE, soilRows, e.startUtc());
            }
        }
        events.sort(Comparator.comparing(RainEventDTO::floodPointSlug).thenComparing(RainEventDTO::startUtc));
        log.info("eventos-chuva: {} ponto(s), {} evento(s), fonte {}", points.size(), events.size(),
                p.source().pipelineSource);
        return new Extraction(slugs, events, coverage, stations, runs, revisions, terrainRunId, radiusM, sensorLinkageId,
                linkage.map(OccurrenceRepository.LinkageRef::id).orElse(null),
                tz.map(RainEventReadRepository.SourceTz::sourceTz).orElse(null),
                tz.map(RainEventReadRepository.SourceTz::verificationState).orElse(null));
    }

    private static final String RIVER_SOURCE = "ANA_SOAP";

    /**
     * Série de chuva efetiva do ponto (ADR 0018). {@code origin}: MEASURED (pluviômetro dentro do raio), REANALYSIS
     * (pedida) ou REANALYSIS_FALLBACK (pluviômetro pedido, nenhum dentro do raio).
     */
    private record RainStation(String source, String id, Double distanceM, String origin, int readingsPerHour,
                               boolean subHourly, String resolution) {}

    private Optional<RainStation> rainStation(RainEventParams p, FloodPoint pt, double radiusM) {
        if (!p.source().measured()) return Optional.of(reanalysis(pt, "REANALYSIS"));
        var src = p.source();
        var near = repo.nearestStation(src.pipelineSource, "precipitation", src.resolution, pt.getLatitude(),
                pt.getLongitude());
        if (near.isPresent() && near.get().distanceM() <= radiusM) {
            return Optional.of(new RainStation(src.pipelineSource, near.get().stationId(),
                    round(near.get().distanceM(), 1), "MEASURED", src.readingsPerHour, src.subHourly(), src.resolution));
        }
        return Optional.of(reanalysis(pt, "REANALYSIS_FALLBACK"));
    }

    private RainStation reanalysis(FloodPoint pt, String origin) {
        var om = RainEventParams.RainSource.OPEN_METEO_ARCHIVE;
        String id = OpenMeteoArchiveAdapter.stationId(pt.getLatitude(), pt.getLongitude());
        Double d = repo.distanceTo(om.pipelineSource, id, pt.getLatitude(), pt.getLongitude()).map(v -> round(v, 1))
                .orElse(null);
        return new RainStation(om.pipelineSource, id, d, origin, om.readingsPerHour, false, om.resolution);
    }

    private RainEventDTO row(RainEventParams p, String slug, RainStation st, double radiusM,
                             RainEventReadRepository.StationRow river, Double alert, DetectedEvent e,
                             List<HourSample> series, Map<Instant, Integer> hourIndex, Map<Instant, Double> sub,
                             Map<Instant, Double> tide, TideFeatureCalculator.Index tideIndex,
                             Map<Instant, Double> soil, java.util.NavigableMap<Instant, Double> riverLevel,
                             TerrainRepository.PointRow terrain, List<OccurrenceRepository.MatchedOccurrence> occs) {
        boolean truncated = e.touchesSeriesEdge() || e.startUtc().isBefore(p.from()) || e.endUtc().isAfter(p.to());

        // chuva sub-horária (ANA): maior leitura de 15 min no evento × leituras por hora
        Double max15 = null;
        if (st.subHourly()) {
            double m = -1;
            for (var en : sub.entrySet()) {
                if (en.getKey().isAfter(e.startUtc()) && !en.getKey().isAfter(e.endUtc())) m = Math.max(m, en.getValue());
            }
            max15 = m < 0 ? null : round(m * st.readingsPerHour(), 3);
        }

        // maré nas horas cheias do início ao fim
        Double tMax = null, tSum = 0.0;
        int tN = 0, above = 0;
        for (Instant h = e.startUtc(); !h.isAfter(e.endUtc()); h = h.plus(HOUR)) {
            Double v = tide.get(h);
            if (v == null) continue;
            tMax = tMax == null ? v : Math.max(tMax, v);
            tSum += v;
            tN++;
            if (p.tideSillM() != null && v > p.tideSillM()) above++;
        }
        Double range = TideFeatureCalculator.at(e.startUtc(), Map.of(), tideIndex).rangeDayM();
        String regime = range == null ? null : (range >= p.springRangeM() ? "SIZIGIA" : "QUADRATURA");

        // rio: maior leitura com carimbo em (início, fim] do evento (leituras EVENT não entram na grade horária)
        Double riverMax = riverLevel.subMap(e.startUtc(), false, e.endUtc(), true).values().stream()
                .max(Double::compare).orElse(null);

        int first = hourIndex.get(e.startUtc().plus(HOUR));
        LocalDate startLocal = e.startUtc().atZone(RECIFE).toLocalDate();

        String eventId = Hashing.sha256(slug + "|" + e.startUtc() + "|" + st.source() + "|" + p.rulesKey()
                        + "sensorLinkageRadiusM=" + radiusM + ";")
                .substring(0, 16);
        return new RainEventDTO(slug, eventId, st.source(), e.startUtc(), e.endUtc(),
                e.startUtc().atZone(RECIFE).toOffsetDateTime().toString(),
                e.endUtc().atZone(RECIFE).toOffsetDateTime().toString(),
                e.durationMin(), e.dryHoursBefore(), round(e.totalMm(), 3), round(e.meanIntensityMmH(), 3),
                round(e.maxHourlyMmH(), 3), max15,
                RainEventClassifier.classify(p.classification(), e.maxHourlyMmH(), p.idfThresholdMmH()).name(),
                e.peakOffsetMin(), e.expectedReadings(), e.receivedReadings(), e.hasGap(), truncated,
                e.runIds().stream().map(UUID::toString).toList(), st.id(), st.distanceM(), st.origin(),
                tMax == null ? null : round(tMax, 3), tN == 0 ? null : round(tSum / tN, 3),
                p.tideSillM() == null || tN == 0 ? null : above, range, regime,
                river == null ? null : river.stationId(), river == null ? null : round(river.distanceM(), 1),
                riverMax == null ? null : round(riverMax, 3),
                alert == null || riverMax == null ? null : riverMax > alert,
                soil.get(e.startUtc()) == null ? null : round(soil.get(e.startUtc()), 4),
                antecedent(series, first, 24), antecedent(series, first, 72),
                terrain == null ? null : terrain.contributingAreaM2(),
                terrain == null ? null : terrain.meanSlopePct(),
                terrain == null ? null : terrain.streetElevationM(),
                startLocal.getMonthValue(), RainEventClassifier.season(startLocal),
                round(e.totalMm() * p.runoffCoefficient(), 3), round(e.excessOverCapacityMm(), 3),
                e.hoursAboveCapacity(), sameDay(e, occs));
    }

    /** Soma das {@code hours} horas que terminam no início do evento; nula se faltar alguma ou sair da série. */
    static Double antecedent(List<HourSample> series, int firstIndex, int hours) {
        if (firstIndex - hours < 0) return null;
        double sum = 0;
        for (int k = firstIndex - hours; k < firstIndex; k++) {
            Double v = series.get(k).mm();
            if (v == null) return null;
            sum += v;
        }
        return round(sum, 3);
    }

    /** Ocorrência casada com o ponto em algum dia local do evento; nulo se não há linkage_config. */
    static Boolean sameDay(DetectedEvent e, List<OccurrenceRepository.MatchedOccurrence> occs) {
        if (occs == null) return null;
        LocalDate d0 = e.startUtc().atZone(RECIFE).toLocalDate();
        LocalDate d1 = e.endUtc().minusNanos(1).atZone(RECIFE).toLocalDate();
        for (var o : occs) {
            LocalDate o0 = o.from().atZone(RECIFE).toLocalDate();
            LocalDate o1 = (o.to().isAfter(o.from()) ? o.to().minusNanos(1) : o.to()).atZone(RECIFE).toLocalDate();
            if (!o1.isBefore(d0) && !o0.isAfter(d1)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ apoio

    private void validate(RainEventParams p) {
        if (p.from() == null || p.to() == null || !p.from().isBefore(p.to()))
            throw new InvalidRequestException("from e to são obrigatórios e from < to");
        if (Duration.between(p.from(), p.to()).compareTo(MAX_WINDOW) > 0)
            throw new InvalidRequestException("janela máxima de 7 anos");
        if (p.dryGapHours() < 1 || p.dryGapHours() > 72) throw new InvalidRequestException("dryGapHours entre 1 e 72");
        if (p.minEventMm() < 0 || p.minEventMm() > 100) throw new InvalidRequestException("minEventMm entre 0 e 100");
        if (p.idfThresholdMmH() <= 0) throw new InvalidRequestException("idfThresholdMmH deve ser > 0");
        if (p.capacityMmH() <= 0) throw new InvalidRequestException("capacityMmH deve ser > 0");
        if (p.runoffCoefficient() <= 0 || p.runoffCoefficient() > 1)
            throw new InvalidRequestException("runoffCoefficient em (0, 1]");
        if (p.springRangeM() <= 0) throw new InvalidRequestException("springRangeM deve ser > 0");
    }

    private List<FloodPoint> points(String slug) {
        if ("all".equals(slug)) {
            return floodPointRepository.findAll().stream().sorted(Comparator.comparing(FloodPoint::getSlug)).toList();
        }
        return List.of(floodPointRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug)));
    }

    /** Grade contígua de {@code first} a {@code last} (fins de hora); hora sem linha = lacuna com 0 leituras. */
    static List<HourSample> grid(List<HourRow> rows, Instant first, Instant last, int expectedPerHour) {
        Map<Instant, HourRow> byHour = new HashMap<>();
        for (HourRow r : rows) byHour.put(r.hourEndUtc(), r);
        List<HourSample> out = new ArrayList<>();
        for (Instant h = first; !h.isAfter(last); h = h.plus(HOUR)) {
            HourRow r = byHour.get(h);
            out.add(r == null ? new HourSample(h, null, 0, expectedPerHour, List.of())
                    : new HourSample(h, r.value(), r.nReadings(), Math.max(r.nExpected(), expectedPerHour), r.runIds()));
        }
        return out;
    }

    private static RainEventSummarizer.Coverage coverageOf(List<HourSample> series, Instant first, Instant last) {
        long nominal = 0, covered = 0;
        for (HourSample h : series) {
            if (h.hourEnd().isBefore(first) || h.hourEnd().isAfter(last)) continue;
            nominal++;
            if (h.mm() != null) covered++;
        }
        return new RainEventSummarizer.Coverage(nominal, covered);
    }

    private static long hoursBetween(Instant first, Instant last) {
        return Duration.between(first, last).toHours() + 1;
    }

    private static Instant ceilHour(Instant t) {
        Instant f = t.truncatedTo(ChronoUnit.HOURS);
        return f.equals(t) ? f : f.plus(HOUR);
    }

    private static Map<Instant, Double> values(List<HourRow> rows) {
        Map<Instant, Double> m = new HashMap<>();
        for (HourRow r : rows) if (r.value() != null) m.put(r.hourEndUtc(), r.value());
        return m;
    }

    private static void track(Map<String, Set<UUID>> runs, Map<String, Integer> revisions, String key, HourRow r) {
        if (r.runIds().isEmpty()) return;
        runs.computeIfAbsent(key, k -> new TreeSet<>()).addAll(r.runIds());
        revisions.merge(key, r.maxRevision(), Math::max);
    }

    private static void trackAt(Map<String, Set<UUID>> runs, Map<String, Integer> revisions, String key,
                                List<HourRow> rows, Instant hour) {
        for (HourRow r : rows) {
            if (r.hourEndUtc().equals(hour) && r.value() != null) track(runs, revisions, key, r);
        }
    }

    private static Double parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Double round(Double v, int scale) {
        if (v == null) return null;
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }

    // ------------------------------------------------------------------------------------------------ manifesto

    private RainEventManifestDTO manifest(RainEventParams p, Extraction x, String kind, String datasetId, int rows) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("slug", p.slug());
        params.put("source", p.source().name());
        params.putAll(p.rulesMap());
        params.put("sensorLinkageRadiusM", x.sensorRadiusM());
        params.put("sensorLinkageConfigId", x.sensorLinkageId());
        List<ExportManifestDTO.SourceProvenance> prov = x.runs().entrySet().stream()
                .map(en -> new ExportManifestDTO.SourceProvenance(en.getKey().split("\\|")[0], en.getKey().split("\\|")[1],
                        List.copyOf(en.getValue()), x.revisions().getOrDefault(en.getKey(), 0))).toList();
        List<String> limitations = new ArrayList<>(List.of(
                "ANA (ANA_SOAP): pluviômetro mais próximo dentro do raio do ADR 0018; hora com menos de 4 leituras de 15 min "
                        + "é lacuna. As estações ANA da RMR ficam a 11 km ou mais dos pontos: fora do raio de 5 km.",
                "CEMADEN: soma dos blocos de 10/60 min com fim na hora (V22); max15minMmH vazio.",
                "OPEN_METEO_ARCHIVE: reanálise em grade (ERA5 via Open-Meteo), chuva horária suavizada no espaço e no "
                        + "tempo; o pico e a contagem de eventos fortes ficam abaixo do pluviômetro. max15minMmH vazio.",
                "Maré prevista (tábua), não medida; alturas em NR DHN, não comparáveis à cota do MDT (ADR 0009). Sem "
                        + "tideSillM, hoursTideAboveSill fica vazio.",
                "riverAboveAlert vazio quando a estação não tem cota de alerta registrada (station.attrs.alertLevelM).",
                "Capacidade da galeria e soleira são [sem fonte] por ponto (PARAMETROS-MODELO-FISICO.md): capacityMmH é "
                        + "um valor único de projeto (TR 5 anos, 60 min), não medido."));
        long truncated = x.events().stream().filter(e -> Boolean.TRUE.equals(e.truncated())).count();
        long gaps = x.events().stream().filter(e -> Boolean.TRUE.equals(e.hasGap())).count();
        return new RainEventManifestDTO(datasetId, kind, RainEventParams.METHOD_VERSION, ingestionProps.getCodeVersion(),
                x.slugs(), p.from(), p.to(), "UTC", RECIFE.getId(), p.source().pipelineSource, x.sourceTz(),
                x.sourceVerification(), params, x.stations(), prov, x.terrainRunId(), x.linkageId(), rows,
                (int) truncated, (int) gaps, RULES, limitations, List.of("ckanOccurrenceSameDay"), CIRCULARITY_WARNING);
    }
}

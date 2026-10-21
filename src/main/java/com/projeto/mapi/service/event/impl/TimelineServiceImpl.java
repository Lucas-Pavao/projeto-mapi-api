package com.projeto.mapi.service.event.impl;

import com.projeto.mapi.dto.PredictionSummaryDTO;
import com.projeto.mapi.dto.TimelineDTO;
import com.projeto.mapi.dto.TimelineDTO.StationSeries;
import com.projeto.mapi.dto.TimelineDTO.TimePoint;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.mapper.PredictionSummaryMapper;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.event.TideInterpolation;
import com.projeto.mapi.service.event.TimelineService;
import com.projeto.mapi.service.flood.FloodPredictionHistoryService;
import com.projeto.mapi.service.sensor.HourlyRainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    static final Duration MAX_WINDOW = Duration.ofHours(72);
    static final Duration STEP = Duration.ofMinutes(10);
    static final String HARBOR = "pe02";

    private final FloodPointRepository pointRepository;
    private final FloodEventRepository eventRepository;
    private final SensorDataRepository sensorRepository;
    private final ObservationReadRepository observations;
    private final FloodPredictionHistoryService history;
    private final PredictionSummaryMapper summaryMapper;

    @Override
    @Transactional(readOnly = true)
    public TimelineDTO timeline(Instant from, Instant to, List<String> slugs) {
        if (!from.isBefore(to)) throw new InvalidRequestException("from deve ser anterior a to");
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) throw new InvalidRequestException("Janela máxima de 72 h");

        List<FloodPoint> points = slugs.isEmpty() ? pointRepository.findByActiveTrue() : slugs.stream()
                .map(s -> pointRepository.findBySlug(s)
                        .orElseThrow(() -> new InvalidRequestException("Ponto não encontrado: " + s)))
                .toList();
        // Com slugs informados, chuva e rio ficam restritos às estações vinculadas a esses pontos.
        Set<String> rainStations = slugs.isEmpty() ? null : union(points, true);
        Set<String> riverStations = slugs.isEmpty() ? null : union(points, false);

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime lf = LocalDateTime.ofInstant(from, zone), lt = LocalDateTime.ofInstant(to, zone);
        String step = STEP.toMinutes() + " minutes";

        // Chuva: soma horária de observation.precipitation, como vem (um ponto por hora, t = fim da hora).
        List<StationSeries> rain = hourlyRain(from, to, rainStations);
        List<StationSeries> river = group(sensorRepository.riverBuckets(lf, lt, step), riverStations, zone);

        List<TideInterpolation.Extreme> extremes = observations
                .tideExtremes(from.minus(MAX_WINDOW.dividedBy(9)), to.plus(MAX_WINDOW.dividedBy(9))).stream()
                .map(e -> new TideInterpolation.Extreme(e.tsUtc(), e.level())).toList();
        List<TimePoint> tidePoints = TideInterpolation.sample(extremes, from, to, STEP).stream()
                .map(s -> new TimePoint(s.t(), s.v())).toList();

        List<TimelineDTO.RiskSeries> risk = new ArrayList<>();
        for (FloodPoint p : points) {
            List<PredictionSummaryDTO> items = history.findSeries(p.getId(), from, to).stream()
                    .map(r -> summaryMapper.toSummary(r, p.getSlug())).toList();
            risk.add(new TimelineDTO.RiskSeries(p.getSlug(), items));
        }

        Set<Long> ids = new LinkedHashSet<>();
        points.forEach(p -> ids.add(p.getId()));
        List<TimelineDTO.TimelineFloodEvent> events = eventRepository
                .findByStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(lf, lt).stream()
                .filter(e -> ids.contains(e.getFloodPoint().getId()))
                .map(e -> new TimelineDTO.TimelineFloodEvent(e.getId(), e.getFloodPoint().getSlug(),
                        e.getStartTime().atZone(zone).toInstant(), e.getSeverity() == null ? null : e.getSeverity().name()))
                .toList();

        return new TimelineDTO(from, to, (int) STEP.toMinutes(), rain,
                new TimelineDTO.TideSeries(HARBOR, tidePoints), river, risk, events);
    }

    private List<StationSeries> hourlyRain(Instant from, Instant to, Set<String> only) {
        Map<String, List<TimePoint>> by = new LinkedHashMap<>();
        for (ObservationReadRepository.HourlyRainRow r : observations.hourlyRain(from, to)) {
            if (only != null && only.stream().noneMatch(id -> HourlyRainService.sameStation(r.stationId(), id))) continue;
            by.computeIfAbsent(r.stationId(), k -> new ArrayList<>()).add(new TimePoint(r.hourEndUtc(), r.mm()));
        }
        return by.entrySet().stream().map(e -> new StationSeries(e.getKey(), e.getValue())).toList();
    }

    private static Set<String> union(List<FloodPoint> points, boolean rain) {
        Set<String> out = new LinkedHashSet<>();
        for (FloodPoint p : points) out.addAll(rain ? p.getPluviometerStationIds() : p.getRiverLevelStationIds());
        return out;
    }

    private static List<StationSeries> group(List<SensorDataRepository.BucketRow> rows, Set<String> only, ZoneId zone) {
        Map<String, List<TimePoint>> by = new LinkedHashMap<>();
        for (SensorDataRepository.BucketRow r : rows) {
            if (only != null && !only.contains(r.getSensorId())) continue;
            by.computeIfAbsent(r.getSensorId(), k -> new ArrayList<>())
                    .add(new TimePoint(r.getBucket().toLocalDateTime().atZone(zone).toInstant(), r.getVal()));
        }
        return by.entrySet().stream().map(e -> new StationSeries(e.getKey(), e.getValue())).toList();
    }
}

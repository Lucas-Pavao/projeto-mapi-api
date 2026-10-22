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
import com.projeto.mapi.repository.TideTableRepository;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.event.TideInterpolation;
import com.projeto.mapi.service.event.TimelineService;
import com.projeto.mapi.service.flood.FloodPredictionHistoryService;
import com.projeto.mapi.service.ingestion.adapter.tide.TideConstants;
import com.projeto.mapi.service.sensor.HourlyRainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
    /** Fuso da tábua da DHN do Porto do Recife ("UTC -03.0"), sem horário de verão. */
    static final ZoneOffset TABLE_OFFSET = ZoneOffset.ofHours(-3);

    private final FloodPointRepository pointRepository;
    private final FloodEventRepository eventRepository;
    private final SensorDataRepository sensorRepository;
    private final ObservationReadRepository observations;
    private final TideTableRepository tideTables;
    private final FloodPredictionHistoryService history;
    private final PredictionSummaryMapper summaryMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TimelineDTO timeline(Instant from, Instant to, List<String> slugs) {
        if (!from.isBefore(to)) throw new InvalidRequestException("from deve ser anterior a to");
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) throw new InvalidRequestException("Janela máxima de 72 h");
        Instant now = clock.instant();
        if (to.isAfter(now.plus(MAX_WINDOW))) throw new InvalidRequestException("to pode ir no máximo até agora + 72 h");
        // Só a maré é prevista: chuva, rio, risco e eventos observados/gravados param em agora.
        Instant dataTo = to.isAfter(now) ? now : to;

        List<FloodPoint> points = slugs.isEmpty() ? pointRepository.findByActiveTrue() : slugs.stream()
                .map(s -> pointRepository.findBySlug(s)
                        .orElseThrow(() -> new InvalidRequestException("Ponto não encontrado: " + s)))
                .toList();
        // Com slugs informados, chuva e rio ficam restritos às estações vinculadas a esses pontos.
        Set<String> rainStations = slugs.isEmpty() ? null : union(points, true);
        Set<String> riverStations = slugs.isEmpty() ? null : union(points, false);

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime lf = LocalDateTime.ofInstant(from, zone), lt = LocalDateTime.ofInstant(dataTo, zone);
        String step = STEP.toMinutes() + " minutes";

        // Chuva: soma horária de observation.precipitation, como vem (um ponto por hora, t = fim da hora).
        List<StationSeries> rain = hourlyRain(from, dataTo, rainStations);
        List<StationSeries> river = group(sensorRepository.riverBuckets(lf, lt, step), riverStations, zone);

        Instant tideFrom = from.minus(MAX_WINDOW.dividedBy(9)), tideTo = to.plus(MAX_WINDOW.dividedBy(9));
        List<TideInterpolation.Extreme> extremes = observations.tideExtremes(tideFrom, tideTo).stream()
                .map(e -> new TideInterpolation.Extreme(e.tsUtc(), e.level())).toList();
        // Reserva: sem extremos em observation (banco recriado, carga da TabuaMare ainda não rodou), usa a tábua
        // local da tela Maré. É a mesma tábua da DHN, no mesmo NR.
        if (extremes.isEmpty()) extremes = tableExtremes(tideFrom, tideTo);
        List<TimePoint> tidePoints = TideInterpolation.sample(extremes, from, to, STEP).stream()
                .map(s -> new TimePoint(s.t(), s.v())).toList();

        List<TimelineDTO.RiskSeries> risk = new ArrayList<>();
        for (FloodPoint p : points) {
            List<PredictionSummaryDTO> items = (from.isBefore(dataTo) ? history.findSeries(p.getId(), from, dataTo) : List.<com.projeto.mapi.dto.FloodPredictionResponseDTO>of()).stream()
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

    /** Extremos da tábua local ({@code tide_tables}) do Porto do Recife em {@code [fromUtc, toUtc)}, em ordem. */
    List<TideInterpolation.Extreme> tableExtremes(Instant fromUtc, Instant toUtc) {
        List<TideInterpolation.Extreme> out = new ArrayList<>();
        int firstYear = fromUtc.atOffset(TABLE_OFFSET).getYear(), lastYear = toUtc.atOffset(TABLE_OFFSET).getYear();
        for (int year = firstYear; year <= lastYear; year++) {
            for (var table : tideTables.findAllByHarborNameIgnoreCaseAndYear(TideConstants.HARBOR_NAME, year)) {
                if (table.getMonths() == null) continue;
                for (var month : table.getMonths()) {
                    if (month.getDays() == null || month.getMonth() == null) continue;
                    for (var day : month.getDays()) {
                        if (day.getHours() == null || day.getDay() == null) continue;
                        for (var h : day.getHours()) {
                            if (h.getHour() == null || h.getLevel() == null) continue;
                            Instant t;
                            try {
                                t = LocalDate.of(year, month.getMonth(), day.getDay())
                                        .atTime(LocalTime.parse(h.getHour())).toInstant(TABLE_OFFSET);
                            } catch (java.time.DateTimeException e) {
                                continue; // hora ou dia malformado na tábua: o extremo é ignorado
                            }
                            if (!t.isBefore(fromUtc) && t.isBefore(toUtc)) {
                                out.add(new TideInterpolation.Extreme(t, h.getLevel()));
                            }
                        }
                    }
                }
            }
        }
        out.sort(java.util.Comparator.comparing(TideInterpolation.Extreme::ts));
        return out;
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

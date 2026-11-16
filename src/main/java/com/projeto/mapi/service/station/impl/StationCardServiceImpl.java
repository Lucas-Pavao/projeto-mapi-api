package com.projeto.mapi.service.station.impl;

import com.projeto.mapi.dto.LocationAnalysisDTO;
import com.projeto.mapi.dto.MonthCheckDTO;
import com.projeto.mapi.dto.PointRainCandidatesDTO;
import com.projeto.mapi.dto.StationCardDTO;
import com.projeto.mapi.dto.StationCardDTO.Block;
import com.projeto.mapi.dto.StationCardDTO.Note;
import com.projeto.mapi.dto.StationCardDTO.Use;
import com.projeto.mapi.dto.StationHourlyDTO;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.repository.analise.PointRainRepository.MonthCheckRow;
import com.projeto.mapi.repository.station.StationCanonicalRepository;
import com.projeto.mapi.repository.station.StationCanonicalRepository.CanonicalView;
import com.projeto.mapi.repository.station.StationCardRepository;
import com.projeto.mapi.repository.station.StationCardRepository.BlockRow;
import com.projeto.mapi.service.analise.PointRainCandidatesService;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.location.LocationAnalysisService;
import com.projeto.mapi.service.sensor.cemaden.impl.MeasuredRainAccumulationServiceImpl;
import com.projeto.mapi.service.station.StationCardService;
import com.projeto.mapi.service.station.StationHourlyService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Card do pluviômetro. Blocos de 10 min vêm de {@code observation_current} (PED se houver, senão ao vivo). Uma leitura
 * de bloco longo (a coleta ao vivo fecha 60 min quando está seco) cobre os baldes de 10 min dentro do seu intervalo:
 * o valor fica no balde do carimbo e os anteriores contam 0, como na grade horária (V22). Horas (divergência, pico
 * isolado, acumulado de 24 h) vêm de {@link StationHourlyService}, a mesma regra da série horária.
 */
@Service
@RequiredArgsConstructor
public class StationCardServiceImpl implements StationCardService {

    static final int BLOCKS = 18;
    static final long BLOCK_S = 600;
    /** O bloco seco da coleta ao vivo dura 60 min (+10 de folga): sem leitura há mais que isso é lacuna. */
    static final Duration GAP_AFTER = Duration.ofMinutes(70);
    static final double USES_RADIUS_M = 5000;
    /** Sem leitura nas 3 h (NO_RESPONSE), o card ainda diz quando a estação falou pela última vez, até 30 dias atrás. */
    static final Duration LAST_READING_LOOKBACK = Duration.ofDays(30);

    private final StationCanonicalRepository canonicals;
    private final StationCardRepository cards;
    private final PointRainRepository rain;
    private final StationHourlyService hourly;
    private final PointRainCandidatesService candidates;
    private final FloodPointRepository floodPoints;
    private final LocationAnalysisService locations;

    @Override
    public StationCardDTO card(String canonicalId, Instant at) {
        Instant ref = at != null ? at : Instant.now();
        CanonicalView c = canonicals.canonicals().stream().filter(x -> x.canonicalId().equals(canonicalId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Estação canônica não encontrada: " + canonicalId));
        List<String> aliases = c.aliases();
        TreeSet<String> sources = new TreeSet<>();
        for (String a : aliases) sources.add(a.substring(0, a.indexOf(':')));
        String pedId = aliasId(aliases, StationUnificationServiceImpl.PED);

        Instant hourFloor = ref.truncatedTo(ChronoUnit.HOURS);
        Instant hourCeil = hourFloor.equals(ref) ? hourFloor : hourFloor.plus(1, ChronoUnit.HOURS);
        Map<Instant, StationHourlyDTO.Hour> hours = new HashMap<>();
        for (StationHourlyDTO.Hour h : hourly.hourly(canonicalId, hourFloor.minus(24, ChronoUnit.HOURS), hourCeil).hours()) {
            hours.put(h.hourEndUtc(), h);
        }

        // --- blocos de 10 min das 3 h ---
        Instant lastBlockEnd = Instant.ofEpochSecond(Math.floorDiv(ref.getEpochSecond(), BLOCK_S) * BLOCK_S);
        Instant windowStart = lastBlockEnd.minusSeconds(BLOCKS * BLOCK_S);  // lastReading usa a mesma janela de 3 h
        Map<String, Map<Instant, Double>> bySource = bucketize(cards.blocks(aliases, windowStart, lastBlockEnd));
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < BLOCKS; i++) {
            Instant end = windowStart.plusSeconds((i + 1) * BLOCK_S);
            Double ped = bySource.getOrDefault(StationUnificationServiceImpl.PED, Map.of()).get(end);
            Double live = bySource.getOrDefault(StationUnificationServiceImpl.LIVE, Map.of()).get(end);
            String src = ped != null ? StationUnificationServiceImpl.PED : live != null ? StationUnificationServiceImpl.LIVE : null;
            StationHourlyDTO.Hour h = hours.get(hourEndOf(end));
            boolean peak = h != null && h.isolatedPeakRemoved();
            blocks.add(new Block(end, peak ? null : ped != null ? ped : live, src, peak));
        }

        Double acc1h = sum(blocks.subList(BLOCKS - 6, BLOCKS));
        Double acc3h = sum(blocks);
        Double acc24h = sumHours(hours, hourFloor, 24);

        StationHourlyDTO.Hour now = hours.get(hourFloor);
        Double pedNow = now == null ? null : now.pedMm(), liveNow = now == null ? null : now.liveMm();
        boolean pedPending = pedId != null && pedNow == null && liveNow != null;

        // --- mês, quarentena, última leitura ---
        MonthCheckDTO month = monthCheck(pedId, ref);
        Instant day = ref.truncatedTo(ChronoUnit.DAYS);
        Map<String, Integer> quarantine = new java.util.LinkedHashMap<>();
        Map<String, Integer> open = cards.openQuarantine(aliases, day, day.plus(1, ChronoUnit.DAYS));
        for (String r : List.of("GAP", "AFTER_GAP", "SUSPECT_QUALIFIER")) quarantine.put(r, open.getOrDefault(r, 0));
        Instant last = cards.lastReading(aliases, windowStart, ref).orElse(null);

        // --- estado (prioridade escrita no contrato B6) e aviso ---
        String state;
        Note note;
        StationHourlyDTO.Hour div = latestDivergent(hours, hourFloor);
        Block peakBlock = null;
        for (Block b : blocks) if (b.peakRemoved()) peakBlock = b;
        if (last == null) {
            state = "NO_RESPONSE";                      // nenhuma leitura de nenhuma fonte nas 3 h
            note = new Note(state, null, null, null, null, null, null, null, null, null);
        } else if (Duration.between(last, ref).compareTo(GAP_AFTER) >= 0) {
            state = "GAP_NOW";                          // nenhuma leitura em (at - 70 min, at], com alguma nas 3 h
            note = new Note(state, null, lastBlockEnd, null, null, null, null, last,
                    Duration.between(last, ref).toMinutes(), null);
        } else if (month != null && ("FAIL".equals(month.status()) || "OFFLINE".equals(month.status()))) {
            state = "FAIL_MONTH";
            note = new Note(state, null, null, null, null, null, null, null, null, month.reasons());
        } else if (div != null) {
            state = "DIVERGENT";
            note = new Note(state, div.hourEndUtc(), null, div.liveMm(), div.pedMm(),
                    round(Math.abs(div.pedMm() - div.liveMm())), null, null, null, null);
        } else if (peakBlock != null) {
            state = "PEAK_REMOVED";
            StationHourlyDTO.Hour ph = hours.get(hourEndOf(peakBlock.endUtc()));
            note = new Note(state, hourEndOf(peakBlock.endUtc()), null, null, null, null,
                    ph == null ? null : ph.pedMm() != null ? ph.pedMm() : ph.liveMm(), null, null, null);
        } else if (month == null || "UNCHECKED".equals(month.status())) {
            state = "UNCHECKED";
            note = new Note(state, null, null, null, null, null, null, null, null, month == null ? List.of() : month.reasons());
        } else {
            state = "NORMAL";
            note = null;
        }

        return new StationCardDTO(canonicalId, c.name(), aliases,
                cards.aliasDistanceM(canonicalId, StationUnificationServiceImpl.LIVE).orElse(null),
                List.copyOf(sources), sources.equals(new TreeSet<>(List.of(StationUnificationServiceImpl.PED))), state,
                now == null ? null : now.valueMm(), now == null ? null : now.usedSource(), liveNow, pedNow, pedPending,
                blocks, acc1h, acc3h, acc24h, note, month, quarantine, uses(c, pedId, hourFloor),
                last != null ? last : cards.lastReading(aliases, ref.minus(LAST_READING_LOOKBACK), ref).orElse(null),
                MeasuredRainAccumulationServiceImpl.ATTRIBUTION);
    }

    /** Soma por balde de 10 min (fim do balde) e por fonte; o valor vai ao balde do carimbo, os anteriores do bloco contam 0. */
    private static Map<String, Map<Instant, Double>> bucketize(List<BlockRow> rows) {
        Map<String, Map<Instant, Double>> out = new HashMap<>();
        for (BlockRow r : rows) {
            Map<Instant, Double> m = out.computeIfAbsent(r.source(), k -> new HashMap<>());
            Instant start = r.intervalStartUtc() != null && r.intervalStartUtc().isBefore(r.tsUtc())
                    ? r.intervalStartUtc() : r.tsUtc().minusSeconds(BLOCK_S);
            Instant end = Instant.ofEpochSecond(Math.ceilDiv(r.tsUtc().getEpochSecond(), BLOCK_S) * BLOCK_S);
            for (Instant e = end.minusSeconds(BLOCK_S); e.isAfter(start); e = e.minusSeconds(BLOCK_S)) m.merge(e, 0.0, Double::sum);
            m.merge(end, r.value(), Double::sum);
        }
        return out;
    }

    private static Instant hourEndOf(Instant t) {
        Instant f = t.truncatedTo(ChronoUnit.HOURS);
        return f.equals(t) ? t : f.plus(1, ChronoUnit.HOURS);
    }

    private static Double sum(List<Block> bs) {
        double s = 0;
        for (Block b : bs) {
            if (b.mm() == null) return null;
            s += b.mm();
        }
        return round(s);
    }

    private static Double sumHours(Map<Instant, StationHourlyDTO.Hour> hours, Instant lastEnd, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) {
            StationHourlyDTO.Hour h = hours.get(lastEnd.minus(i, ChronoUnit.HOURS));
            if (h == null || h.valueMm() == null) return null;
            s += h.valueMm();
        }
        return round(s);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Divergência nas 2 últimas horas fechadas; a mais recente. */
    private static StationHourlyDTO.Hour latestDivergent(Map<Instant, StationHourlyDTO.Hour> hours, Instant hourFloor) {
        for (int i = 0; i < 2; i++) {
            StationHourlyDTO.Hour h = hours.get(hourFloor.minus(i, ChronoUnit.HOURS));
            if (h != null && h.divergent()) return h;
        }
        return null;
    }

    private static String aliasId(List<String> aliases, String source) {
        for (String a : aliases) if (a.startsWith(source + ":")) return a.substring(source.length() + 1);
        return null;
    }

    private MonthCheckDTO monthCheck(String pedId, Instant ref) {
        if (pedId == null) return null;
        Instant start = ref.atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant next = ref.atZone(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(1).toInstant();
        for (MonthCheckRow r : rain.monthChecks(PointRainService.SOURCE, PointRainService.RULE_VERSION, start, next)) {
            if (r.stationId().equals(pedId)) {
                return new MonthCheckDTO(r.monthStart(), "PASS".equals(r.status()) ? "OK" : r.status(), r.reasons(),
                        PointRainService.RULE_VERSION);
            }
        }
        return null;
    }

    /**
     * Pontos a até 5 km e o papel desta estação na hora. Papel da série do ponto (B3); sem série do ponto, o mesmo
     * critério sobre os valores horários das candidatas (maior valor entre as válidas, empate pela mais próxima).
     */
    private List<Use> uses(CanonicalView c, String pedId, Instant hourEnd) {
        List<Use> out = new ArrayList<>();
        List<FloodPoint> near = new ArrayList<>();
        for (FloodPoint p : floodPoints.findByActiveTrue()) {
            if (p.getLatitude() != null && p.getLongitude() != null
                    && distanceM(c.lat(), c.lon(), p.getLatitude(), p.getLongitude()) <= USES_RADIUS_M) near.add(p);
        }
        near.sort(java.util.Comparator.comparingDouble(p -> distanceM(c.lat(), c.lon(), p.getLatitude(), p.getLongitude())));
        for (FloodPoint p : near) {
            PointRainCandidatesDTO dto = pedId == null ? null : candidates.candidates(p.getSlug(), hourEnd);
            PointRainCandidatesDTO.Candidate mine = dto == null ? null : dto.candidates().stream()
                    .filter(x -> x.stationId().equals(pedId)).findFirst().orElse(null);
            if (mine == null) {
                // A série do ponto não cobre a hora (a carga PED parou) ou a estação é só da coleta ao vivo:
                // vale o valor horário canônico, pela mesma regra da análise de local (contrato B6, `uses`).
                Use canonical = canonicalUse(c, p, hourEnd);
                if (canonical != null) out.add(canonical);
                continue;
            }
            String role = mine.role();
            String usedName = null;
            if (dto.usedStationId() == null && !"EXCLUDED".equals(role)) {
                PointRainCandidatesDTO.Candidate best = null;
                for (PointRainCandidatesDTO.Candidate x : dto.candidates()) {
                    if ("EXCLUDED".equals(x.role()) || x.valueMm() == null) continue;
                    if (best == null || x.valueMm() > best.valueMm()) best = x;
                }
                role = mine.valueMm() == null ? "NO_VALUE" : best == mine ? "USED" : "LOWER";
                if ("LOWER".equals(role) && best != null) usedName = best.name();
            } else if ("LOWER".equals(role)) {
                usedName = dto.candidates().stream().filter(x -> x.stationId().equals(dto.usedStationId()))
                        .map(PointRainCandidatesDTO.Candidate::name).filter(Objects::nonNull).findFirst().orElse(null);
            }
            out.add(new Use(p.getSlug(), p.getName(), Math.round(distanceM(c.lat(), c.lon(), p.getLatitude(), p.getLongitude())),
                    role, usedName));
        }
        return out;
    }

    private Use canonicalUse(CanonicalView c, FloodPoint p, Instant hourEnd) {
        LocationAnalysisDTO.Rain r = locations.measuredRain(p.getLatitude(), p.getLongitude(), hourEnd);
        LocationAnalysisDTO.Candidate mine = r.candidates().stream()
                .filter(x -> x.stationId().equals(c.canonicalId())).findFirst().orElse(null);
        if (mine == null) return null;
        String usedName = !"LOWER".equals(mine.role()) || r.station() == null ? null : r.station().name();
        return new Use(p.getSlug(), p.getName(), Math.round(distanceM(c.lat(), c.lon(), p.getLatitude(), p.getLongitude())),
                mine.role(), usedName);
    }

    private static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        return GeoUtils.calculateDistance(lat1, lon1, lat2, lon2) * 1000.0;
    }
}

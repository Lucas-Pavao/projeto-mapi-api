package com.projeto.mapi.service.station.impl;

import com.projeto.mapi.dto.CanonicalStationDTO;
import com.projeto.mapi.dto.MonthCheckDTO;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.sensor.cemaden.impl.MeasuredRainAccumulationServiceImpl;
import com.projeto.mapi.dto.StationUnificationReportDTO;
import com.projeto.mapi.repository.station.StationCanonicalRepository;
import com.projeto.mapi.repository.station.StationCanonicalRepository.AliasRow;
import com.projeto.mapi.repository.station.StationCanonicalRepository.CanonicalRow;
import com.projeto.mapi.repository.station.StationCanonicalRepository.DivergenceRow;
import com.projeto.mapi.repository.station.StationCanonicalRepository.HourValue;
import com.projeto.mapi.service.ingestion.adapter.CemadenAdapter;
import com.projeto.mapi.service.station.StationMatcher;
import com.projeto.mapi.service.station.StationMatcher.Alias;
import com.projeto.mapi.service.station.StationMatcher.Canonical;
import com.projeto.mapi.service.station.StationMatcher.Station;
import com.projeto.mapi.service.station.StationUnificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unificação por coordenada (ADR 0023). A PED define as canônicas; cada estação da coleta ao vivo a até
 * {@code maxDistanceM} da PED mais próxima vira apelido dela. Nas horas em que as duas fontes têm valor, a série
 * canônica ({@code canonical_rain_hourly_v}) fica com a PED e a diferença acima da tolerância é gravada em
 * {@code station_hour_divergence}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StationUnificationServiceImpl implements StationUnificationService {

    public static final String RULE_VERSION = "estacao-canonica-v1";
    public static final String PED = "CEMADEN_PED";
    public static final String LIVE = CemadenAdapter.SOURCE;
    /** Abaixo disto a semelhança de nome vai para o log como aviso (a decisão continua sendo a distância). */
    private static final double LOW_NAME_SIMILARITY = 0.5;

    private static final List<String> QUARANTINE_REASONS = List.of("GAP", "AFTER_GAP", "SUSPECT_QUALIFIER");

    private final StationCanonicalRepository repo;

    /** 150 m: dentro da incerteza das coordenadas de cadastro com 3 casas decimais (~110 m) `[sem fonte]` como critério. */
    @Value("${mapi.station-unification.max-distance-m:150}")
    private double maxDistanceM = 150;

    /** 0,5 mm na hora `[sem fonte]`: acima de duas básculas de 0,2 mm. */
    @Value("${mapi.station-unification.divergence-tolerance-mm:0.5}")
    private double toleranceMm = 0.5;

    @Override
    @Transactional
    public StationUnificationReportDTO unify() {
        List<Station> ped = repo.stations(PED);
        List<Station> live = repo.stations(LIVE);
        List<Canonical> canonicals = StationMatcher.unify(ped, live, maxDistanceM);

        List<CanonicalRow> cRows = new ArrayList<>();
        List<AliasRow> aRows = new ArrayList<>();
        List<StationUnificationReportDTO.Merge> merges = new ArrayList<>();
        int unified = 0, sameCode = 0, pedOnly = 0, liveOnly = 0;
        for (Canonical c : canonicals) {
            Station p = c.primary();
            cRows.add(new CanonicalRow(c.canonicalId(), cleanName(p.name()), p.lat(), p.lon(), p.municipality(),
                    StationMatcher.ibgeCode(p.stationId()), p.source(), c.aliases().size(), RULE_VERSION));
            boolean hasPed = false, hasLive = false;
            for (Alias a : c.aliases()) {
                Station s = a.station();
                aRows.add(new AliasRow(s.source(), s.stationId(), c.canonicalId(), s.name(), a.distanceM(),
                        a.nameSimilarity(), RULE_VERSION));
                hasPed |= PED.equals(s.source());
                hasLive |= LIVE.equals(s.source());
                if (s != p) {
                    merges.add(new StationUnificationReportDTO.Merge(c.canonicalId(), s.source(), s.stationId(), s.name(),
                            p.name(), round(a.distanceM()), a.nameSimilarity()));
                    if (a.nameSimilarity() == null || a.nameSimilarity() < LOW_NAME_SIMILARITY) {
                        log.warn("Unificação por coordenada com nome diferente: {}:{} '{}' → {} '{}' a {} m (semelhança {})",
                                s.source(), s.stationId(), s.name(), c.canonicalId(), p.name(), round(a.distanceM()),
                                a.nameSimilarity());
                    }
                }
            }
            if (hasPed && hasLive) {
                unified++;
                if (c.aliases().stream().map(a -> a.station().stationId()).distinct().count() == 1) sameCode++;
            } else if (hasPed) {
                pedOnly++;
            } else {
                liveOnly++;
            }
        }
        repo.replaceAll(cRows, aRows);

        // Divergência: só nas canônicas com as duas fontes; uma consulta por estação de origem.
        List<DivergenceRow> divergences = new ArrayList<>();
        int compared = 0;
        Double maxDiff = null;
        for (Canonical c : canonicals) {
            List<Station> pedAliases = c.aliases().stream().map(Alias::station).filter(s -> PED.equals(s.source())).toList();
            List<Station> liveAliases = c.aliases().stream().map(Alias::station).filter(s -> LIVE.equals(s.source())).toList();
            if (pedAliases.isEmpty() || liveAliases.isEmpty()) continue;
            Station pedStation = pedAliases.get(0);
            Map<Instant, Double> pedHours = new HashMap<>();
            for (HourValue h : repo.hourly(PED, pedStation.stationId())) pedHours.put(h.hourEndUtc(), h.value());
            for (Station l : liveAliases) {
                for (HourValue h : repo.hourly(LIVE, l.stationId())) {
                    Double kept = pedHours.get(h.hourEndUtc());
                    if (kept == null) continue;
                    compared++;
                    double diff = Math.abs(kept - h.value());
                    maxDiff = maxDiff == null ? diff : Math.max(maxDiff, diff);
                    if (diff > toleranceMm) {
                        divergences.add(new DivergenceRow(c.canonicalId(), h.hourEndUtc(), PED, pedStation.stationId(), kept,
                                LIVE, l.stationId(), h.value(), round(diff), toleranceMm, RULE_VERSION));
                    }
                }
            }
        }
        repo.insertDivergences(divergences);

        Map<String, Integer> bySource = new LinkedHashMap<>();
        bySource.put(PED, ped.size());
        bySource.put(LIVE, live.size());
        log.info("Unificação {}: {} canônicas ({} unificadas, {} só PED, {} só ao vivo), {} horas comparadas, {} divergências",
                RULE_VERSION, canonicals.size(), unified, pedOnly, liveOnly, compared, divergences.size());
        return new StationUnificationReportDTO(RULE_VERSION, maxDistanceM, toleranceMm, bySource, canonicals.size(),
                unified, sameCode, pedOnly, liveOnly, compared, divergences.size(),
                maxDiff == null ? null : round(maxDiff), merges);
    }

    @Override
    public List<CanonicalStationDTO> canonicalStations() {
        Map<String, StationCanonicalRepository.MonthCheckView> checks = repo.latestMonthChecks(PointRainService.RULE_VERSION);
        Map<String, Map<String, Integer>> quarantine = repo.openQuarantineByCanonical();
        Map<String, Integer> divergences = repo.divergenceCounts();
        return repo.canonicals().stream().map(c -> {
            Map<String, Integer> q = new LinkedHashMap<>();
            for (String reason : QUARANTINE_REASONS) q.put(reason, 0);
            q.putAll(quarantine.getOrDefault(c.canonicalId(), Map.of()));
            var m = checks.get(c.canonicalId());
            MonthCheckDTO monthCheck = m == null ? null : new MonthCheckDTO(m.monthStartUtc(),
                    "PASS".equals(m.status()) ? "OK" : m.status(), m.reasons(), m.ruleVersion());
            List<String> sources = c.aliases().stream().map(a -> a.substring(0, a.indexOf(':'))).distinct().sorted().toList();
            return new CanonicalStationDTO(c.canonicalId(), c.name(), c.municipality(), c.ibgeCode(), c.lat(), c.lon(),
                    c.primarySource(), c.aliases(), sources, monthCheck, q, divergences.getOrDefault(c.canonicalId(), 0),
                    MeasuredRainAccumulationServiceImpl.ATTRIBUTION);
        }).toList();
    }

    /** Tira as etiquetas "[CEMADEN]"/"[APAC]" que a coleta ao vivo põe no nome. */
    static String cleanName(String name) {
        return name == null ? null : name.replaceAll("\\[[^\\]]*\\]", " ").trim().replaceAll("\\s+", " ");
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

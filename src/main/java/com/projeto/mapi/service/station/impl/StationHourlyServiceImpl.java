package com.projeto.mapi.service.station.impl;

import com.projeto.mapi.dto.StationHourlyDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.repository.analise.PointRainRepository;
import com.projeto.mapi.repository.analise.PointRainRepository.HourRow;
import com.projeto.mapi.repository.analise.PointRainRepository.StationRow;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository.HourlyRainRow;
import com.projeto.mapi.repository.station.StationCanonicalRepository;
import com.projeto.mapi.repository.station.StationCanonicalRepository.CanonicalView;
import com.projeto.mapi.service.sensor.RainSpatialCheck;
import com.projeto.mapi.service.sensor.cemaden.impl.MeasuredRainAccumulationServiceImpl;
import com.projeto.mapi.service.station.StationHourlyService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Série horária de uma canônica. Cada estação de origem é lida por consulta própria (a view horária só empurra o
 * filtro por fonte e estação). Pico isolado: regra de {@link RainSpatialCheck}; as vizinhas só são lidas quando a hora
 * tem 10 mm ou mais.
 */
@Service
@RequiredArgsConstructor
public class StationHourlyServiceImpl implements StationHourlyService {

    private final StationCanonicalRepository canonicals;
    private final PointRainRepository rain;

    @Value("${mapi.station-unification.divergence-tolerance-mm:0.5}")
    private double toleranceMm = 0.5;

    @Override
    public StationHourlyDTO hourly(String canonicalId, Instant from, Instant to) {
        if (!to.isAfter(from)) throw new InvalidRequestException("'to' deve ser posterior a 'from'");
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_DAYS)) > 0) {
            throw new InvalidRequestException("Janela máxima de " + MAX_DAYS + " dias");
        }
        CanonicalView c = canonicals.canonicals().stream().filter(x -> x.canonicalId().equals(canonicalId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Estação canônica não encontrada: " + canonicalId));

        Map<String, Map<Instant, Double>> bySource = new HashMap<>();
        for (String alias : c.aliases()) {
            int k = alias.indexOf(':');
            String source = alias.substring(0, k);
            Map<Instant, Double> hours = bySource.computeIfAbsent(source, x -> new HashMap<>());
            for (HourRow h : rain.hourly(source, alias.substring(k + 1), from, to)) {
                if (h.value() != null) hours.putIfAbsent(h.hourEndUtc(), h.value());
            }
        }
        Map<Instant, Double> ped = bySource.getOrDefault(StationUnificationServiceImpl.PED, Map.of());
        Map<Instant, Double> live = bySource.getOrDefault(StationUnificationServiceImpl.LIVE, Map.of());
        Map<String, List<StationRow>> stationsBySource = new HashMap<>();
        Map<String, List<HourRow>> neighborCache = new HashMap<>();

        List<StationHourlyDTO.Hour> out = new ArrayList<>();
        for (Instant h = from.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS); !h.isAfter(to);
             h = h.plus(1, ChronoUnit.HOURS)) {
            Double p = ped.get(h), l = live.get(h);
            String used = p != null ? StationUnificationServiceImpl.PED : l != null ? StationUnificationServiceImpl.LIVE : null;
            Double value = p != null ? p : l;
            boolean divergent = p != null && l != null && Math.abs(p - l) > toleranceMm;
            boolean isolated = value != null && value >= RainSpatialCheck.SUSPECT_MM
                    && isIsolated(c, used, h, value, from, to, stationsBySource, neighborCache);
            out.add(new StationHourlyDTO.Hour(h, isolated ? null : value, used, p, l, divergent, isolated));
        }
        return new StationHourlyDTO(canonicalId, from, to, toleranceMm, out, MeasuredRainAccumulationServiceImpl.ATTRIBUTION);
    }

    private boolean isIsolated(CanonicalView c, String source, Instant hour, double value, Instant from, Instant to,
                               Map<String, List<StationRow>> stationsBySource, Map<String, List<HourRow>> cache) {
        List<StationRow> all = stationsBySource.computeIfAbsent(source, rain::stations);
        String own = c.aliases().stream().filter(a -> a.startsWith(source + ":")).map(a -> a.substring(source.length() + 1))
                .findFirst().orElse("");
        HourlyRainRow self = new HourlyRainRow(source, own, hour, value, c.lat(), c.lon());
        List<HourlyRainRow> rows = new ArrayList<>();
        rows.add(self);
        for (StationRow s : all) {
            if (s.stationId().equals(own) || GeoUtils.calculateDistance(c.lat(), c.lon(), s.lat(), s.lon()) > RainSpatialCheck.RADIUS_KM) continue;
            for (HourRow n : cache.computeIfAbsent(source + ":" + s.stationId(), k -> rain.hourly(source, s.stationId(), from, to))) {
                if (n.value() != null && n.hourEndUtc().equals(hour)) {
                    rows.add(new HourlyRainRow(source, s.stationId(), hour, n.value(), s.lat(), s.lon()));
                }
            }
        }
        return RainSpatialCheck.isIsolatedPeak(self, rows);
    }
}

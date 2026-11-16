package com.projeto.mapi.service.flood.impl;

import com.projeto.mapi.repository.analise.PointPhysicalInputRepository;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.event.TideInterpolation;
import com.projeto.mapi.service.flood.PointPhysicalInputService;
import com.projeto.mapi.service.flood.PointPhysicalInputs;
import com.projeto.mapi.service.flood.PointRainInputs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PointPhysicalInputServiceImpl implements PointPhysicalInputService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration TIDE_WINDOW = Duration.ofHours(12);

    private final PointPhysicalInputRepository repo;
    private final ObservationReadRepository observations;

    @Override
    public PointPhysicalInputs inputsAt(double lat, double lon, PointRainInputs rain) {
        Instant h = rain.hourEndUtc();
        Double max10 = null;
        // Pico de 10 min: da mesma estação e fonte que deram a chuva da hora de referência.
        PointRainInputs.Value current = rain.values().isEmpty() ? null : rain.values().get(0);
        if (current != null && current.valueMm() != null && !current.stations().isEmpty()) {
            PointRainInputs.Station st = current.stations().get(0);
            max10 = safe("pico de 10 min", () -> repo.rainMax10min(st.stationId(), st.source(), h).orElse(null));
        }
        Double trend = safe("tendência da maré", () -> tideTrend(h));
        Double river = safe("nível do rio", () -> repo.riverLevelM(lat, lon, h).orElse(null));
        Double soil = safe("umidade do solo", () -> repo.soilMoisture(lat, lon, h.minus(HOUR)).orElse(null));
        return new PointPhysicalInputs(max10, trend, river, soil);
    }

    /** Nível previsto na hora menos o da hora anterior (m/h), pelos extremos da tábua interpolados (como no mapa). */
    private Double tideTrend(Instant h) {
        List<TideInterpolation.Extreme> ex = observations.tideExtremes(h.minus(TIDE_WINDOW), h.plus(TIDE_WINDOW)).stream()
                .map(e -> new TideInterpolation.Extreme(e.tsUtc(), e.level())).toList();
        List<TideInterpolation.Sample> s = TideInterpolation.sample(ex, h.minus(HOUR), h.plusSeconds(1), HOUR);
        if (s.size() < 2 || s.get(0).v() == null || s.get(1).v() == null) return null;
        return Math.round((s.get(1).v() - s.get(0).v()) * 100.0) / 100.0;
    }

    private static Double safe(String what, java.util.function.Supplier<Double> f) {
        try {
            return f.get();
        } catch (RuntimeException e) {
            log.warn("Entrada física ausente ({}): {}", what, e.getMessage());
            return null;
        }
    }
}

package com.projeto.mapi.service.tide.impl;

import com.projeto.mapi.service.tide.TideLevelService;
import com.projeto.mapi.service.tide.TideSource;

import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.event.TideInterpolation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Maré prevista do Porto do Recife num instante: extremos da TabuaMare/DHN gravados em {@code observation},
 * interpolados por cosseno ({@link TideInterpolation}). É a fonte do mapa, da linha do tempo e da análise de local, e
 * passa a ser a da previsão de alagamento (antes: tábua local vazia para 2026 ou a API TabuaMare ao vivo, barrada pelo
 * limitador de requisições, e o modelo recebia maré ausente).
 */
@Service
@RequiredArgsConstructor
public class TideLevelServiceImpl implements TideLevelService {

    private static final Duration WINDOW = Duration.ofHours(12);
    private static final Duration STEP = Duration.ofMinutes(10);

    private final ObservationReadRepository observations;

    /** Nível (m, NR DHN) e se está enchendo; vazio sem extremos que cerquem o instante. */
    @Override
    public Optional<Level> at(Instant t) {
        var rows = observations.tideExtremes(t.minus(WINDOW), t.plus(WINDOW));
        List<TideInterpolation.Extreme> ex = rows.stream()
                .map(e -> new TideInterpolation.Extreme(e.tsUtc(), e.level())).toList();
        List<TideInterpolation.Sample> s = TideInterpolation.sample(ex, t, t.plus(STEP.multipliedBy(2)), STEP);
        if (s.isEmpty() || s.get(0).v() == null) return Optional.empty();
        Double next = s.size() < 2 ? null : s.get(1).v();
        return Optional.of(new Level(s.get(0).v(), next == null ? null : next > s.get(0).v(),
                TideSource.at(rows, t, r -> r.tsUtc(), r -> r.source())));
    }

    @Override
    public List<Double> series(Instant first, int n, Duration step) {
        if (n <= 0) return List.of();
        Instant last = first.plus(step.multipliedBy(n - 1L));
        List<TideInterpolation.Extreme> ex = observations.tideExtremes(first.minus(WINDOW), last.plus(WINDOW)).stream()
                .map(e -> new TideInterpolation.Extreme(e.tsUtc(), e.level())).toList();
        return TideInterpolation.sample(ex, first, last.plus(step), step).stream().map(TideInterpolation.Sample::v).toList();
    }

}

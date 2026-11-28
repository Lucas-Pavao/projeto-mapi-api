package com.projeto.mapi.service.tide;

import java.time.Instant;
import java.util.Optional;

/**
 * Maré prevista do Porto do Recife num instante, pelos extremos gravados em {@code observation} (mesma fonte do mapa,
 * da linha do tempo e da análise de local).
 */
public interface TideLevelService {

    /** Nível (m, NR DHN) e se está enchendo; vazio sem extremos que cerquem o instante. */
    Optional<Level> at(Instant t);

    /**
     * Nível (m, NR DHN) em {@code n} instantes, de {@code first} a cada {@code step}, numa consulta só (ADR 0030: maré de
     * cada passo da série e do horizonte). {@code null} onde não há extremos que cerquem o instante (nunca maré baixa).
     */
    default java.util.List<Double> series(Instant first, int n, java.time.Duration step) {
        java.util.List<Double> out = new java.util.ArrayList<>(n);
        for (int k = 0; k < n; k++) out.add(at(first.plus(step.multipliedBy(k))).map(Level::levelM).orElse(null));
        return out;
    }

    record Level(double levelM, Boolean rising, TideSource source) {}
}

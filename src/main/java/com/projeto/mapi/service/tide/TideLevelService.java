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

    record Level(double levelM, Boolean rising, TideSource source) {}
}

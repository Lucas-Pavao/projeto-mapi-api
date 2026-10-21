package com.projeto.mapi.service.event;

import com.projeto.mapi.dto.TimelineDTO;

import java.time.Instant;
import java.util.List;

/** Linha do tempo de um evento: chuva, maré prevista, rio, risco por ponto e alagamentos confirmados. Só lê. */
public interface TimelineService {

    /**
     * Janela máxima de 72 h, passo de 10 min. {@code slugs} vazio = todos os pontos ativos;
     * slug inexistente = {@code InvalidRequestException}.
     */
    TimelineDTO timeline(Instant from, Instant to, List<String> slugs);
}

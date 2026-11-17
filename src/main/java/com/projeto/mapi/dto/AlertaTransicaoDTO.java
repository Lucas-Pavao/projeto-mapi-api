package com.projeto.mapi.dto;

import java.time.Instant;

/** Transição do alerta (liga, sobe, desce, desliga, cancela, expira, SEM_DADO, volta do dado). */
public record AlertaTransicaoDTO(long id, String slug, Instant at, String field, String fromState, String toState,
                                 String kind, String reason, String eventId, Double probForte1h, Double rainTeMm,
                                 Double hNowCm, Double hProjQ50MaxCm, Integer floodInMinutes, boolean shadow) {}

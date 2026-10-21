package com.projeto.mapi.service.flood;

import java.time.Instant;

/** Publicado ao fim de cada rodada de previsão do ciclo de coleta (ADR 0011, ADR 0012). */
public record PredictionCycleCompletedEvent(String origin, Instant completedAt) {
}

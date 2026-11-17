package com.projeto.mapi.service.alerta;

import com.projeto.mapi.repository.alerta.AlertaRepository;

import java.util.List;
import java.util.UUID;

/** Ciclo do alerta por ponto (ADR 0026). */
public interface AlertaCycleService {

    record CycleSummary(UUID cycleId, int points, double bucketMsTotal, List<AlertaRepository.State> states) {}

    /** Avalia todos os pontos ativos no instante do relógio injetado. */
    CycleSummary run(String origin);
}

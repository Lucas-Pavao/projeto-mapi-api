package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.PredictionSeriesDTO;
import com.projeto.mapi.dto.PredictionSummaryDTO;
import com.projeto.mapi.dto.RiskSummaryDTO;

import java.time.Instant;
import java.util.List;

/** Leituras de previsão para as telas novas. Só lê {@code flood_predictions}: nunca chama a IA nem grava (ADR 0007 §7). */
public interface PredictionQueryService {

    /** Contagem de pontos ativos por nível, pela última previsão de cada um (sem previsão = UNKNOWN). */
    RiskSummaryDTO riskSummary();

    /** Última previsão de cada ponto ativo; ponto sem previsão não aparece. */
    List<PredictionSummaryDTO> latestPerActivePoint();

    /** Série do ponto ordenada por issuedAt. Janela máxima de 7 dias; 404 se o slug não existe. */
    PredictionSeriesDTO series(String slug, Instant from, Instant to);
}

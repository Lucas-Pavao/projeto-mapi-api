package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.FloodPredictionResponseDTO;

/**
 * Resultado de uma chamada à IA: o payload do contrato (que leva ao cliente só a parte semântica da
 * versão do modelo) e a versão COMPLETA, com o hash git, que fica só no log e na auditoria
 * (ADR 0007 §5). {@code rainForecastUsed}: a IA declarou que o modelo usou a previsão de chuva enviada.
 */
public record PredictionResult(FloodPredictionResponseDTO response, String auditModelVersion, boolean rainForecastUsed) {

    /** Sem previsão de chuva usada pelo modelo (todo modelo até o logreg_v3). */
    public PredictionResult(FloodPredictionResponseDTO response, String auditModelVersion) {
        this(response, auditModelVersion, false);
    }
}

package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Recorte do contrato de predição (ADR 0007) usado por série, risk-summary, SSE e linha do tempo.
 * {@code probability} é null quando {@code riskLevel = UNKNOWN}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PredictionSummaryDTO(
        String slug,
        RiskLevel riskLevel,
        Double probability,
        Instant issuedAt,
        Instant validUntil,
        Integer horizonHours,
        String modelVersion,
        DegradationReason degradationReason,
        Instant lastValidIssuedAt,
        ModelValidationStatus modelValidationStatus,
        // Etapa 8 do IN1165 (por adição): o stream e a série não podem tirar a etiqueta de modelo sintético.
        FloodPredictionResponseDTO.RiskThresholds riskThresholds,
        String trainingData,
        String syntheticWarning,
        // NC-BACK B5: origem da chuva usada pelo modelo (por adição).
        String rainSource,
        // Modelo v3 (C1), por adição: a série distingue v2 e v3 por modelVersion e diz que probabilidade é.
        String probabilityKind) {
}

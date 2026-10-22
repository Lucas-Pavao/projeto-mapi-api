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
        ModelValidationStatus modelValidationStatus) {
}

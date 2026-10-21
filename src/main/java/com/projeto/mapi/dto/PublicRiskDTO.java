package com.projeto.mapi.dto;

import java.time.Instant;

/**
 * Resposta pública de "Minha rua" (ADR 0013). Sem probabilidade numérica, sem coordenada do pedido.
 * {@code meaning} repete o texto de RISK_MEANING do front, ou a mensagem de "sem ponto por perto".
 */
public record PublicRiskDTO(
        NearestPoint nearestPoint,
        Double rainLastHourMm,
        String rainStationId,
        ModelValidationStatus modelValidationStatus,
        String meaning) {

    public record NearestPoint(String slug, String name, int distanceM, RiskLevel riskLevel, Instant issuedAt,
                               Instant validUntil, DegradationReason degradationReason) {
    }
}

package com.projeto.mapi.dto;

import lombok.Builder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Contrato de SAÍDA para o front (camelCase), ADR 0007 §5. Não confundir com o shape bruto da MAPI AI
 * (snake_case, ver FloodPredictionServiceImpl.AiPredictionDTO).
 *
 * <p>As regras de coerência (§2) são conferidas por {@code PredictionCoherence}. Todo instante é UTC e
 * serializa em ISO-8601 com "Z"; é truncado em segundos para a resposta ficar estável.
 *
 * <p>{@code modelValidationStatus} (Emenda 1 do ADR 0007) é sempre preenchido e vale {@code NOT_VALIDATED}
 * por padrão; {@code VALIDATED} só com ADR que cite avaliação pré-registrada positiva (ver {@link ModelValidationStatus}).
 */
@Builder(toBuilder = true)
public record FloodPredictionResponseDTO(
        RiskLevel riskLevel,
        Double floodProbability,
        String modelVersion,
        Instant issuedAt,
        Integer horizonHours,
        Instant validUntil,
        DegradationReason degradationReason,
        Instant lastValidIssuedAt,
        List<Contributor> contributors,
        List<ImputedFeature> imputedFeatures,
        @Deprecated String estimatedTimeToEvent,
        String message,
        ModelValidationStatus modelValidationStatus) {

    public FloodPredictionResponseDTO {
        modelValidationStatus = modelValidationStatus == null ? ModelValidationStatus.NOT_VALIDATED : modelValidationStatus;
        issuedAt = truncate(issuedAt);
        validUntil = truncate(validUntil);
        lastValidIssuedAt = truncate(lastValidIssuedAt);
        contributors = contributors == null ? List.of() : List.copyOf(contributors);
        imputedFeatures = imputedFeatures == null ? List.of() : List.copyOf(imputedFeatures);
    }

    private static Instant truncate(Instant i) {
        return i == null ? null : i.truncatedTo(ChronoUnit.SECONDS);
    }

    /** Fator que mais pesou na previsão; {@code label} vem do catálogo fixo em português. */
    public record Contributor(String featureId, String label, Double contribution) {
    }

    /** Variável que o backend (ou a IA) preencheu por imputação, em vez de medir. */
    public record ImputedFeature(String featureId, String label) {
    }
}

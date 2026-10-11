package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.RiskLevel;

import java.util.Optional;

/** Regras de coerência do ADR 0007 §2, válidas para todo payload emitido pelo backend. */
public final class PredictionCoherence {

    private PredictionCoherence() {
    }

    /** Descrição da primeira violação encontrada, ou vazio se o payload é coerente. */
    public static Optional<String> violation(FloodPredictionResponseDTO p) {
        if (p == null) return Optional.of("payload nulo");
        if (p.riskLevel() == null) return Optional.of("riskLevel nulo");

        if (p.riskLevel() == RiskLevel.UNKNOWN) {
            if (p.floodProbability() != null) return Optional.of("UNKNOWN exige floodProbability nula");
            if (p.degradationReason() == null) return Optional.of("UNKNOWN exige degradationReason");
            return Optional.empty();
        }

        Double prob = p.floodProbability();
        if (prob == null || prob.isNaN() || prob < 0.0 || prob > 1.0) {
            return Optional.of("floodProbability fora de [0,1] ou nula");
        }
        if (p.modelVersion() == null || p.modelVersion().isBlank()) return Optional.of("modelVersion ausente");
        if (p.issuedAt() == null) return Optional.of("issuedAt ausente");
        if (p.degradationReason() != null) return Optional.of("previsão válida não pode ter degradationReason");
        return Optional.empty();
    }
}

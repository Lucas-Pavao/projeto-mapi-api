package com.projeto.mapi.dto;

import java.util.Locale;
import java.util.Optional;

/**
 * Nível de risco do contrato de predição (ADR 0007 §1). {@code UNKNOWN} significa "não há previsão
 * válida" e NÃO é risco baixo. A IA nunca emite {@code UNKNOWN}: quem o atribui é o backend.
 */
public enum RiskLevel {
    LOW, MEDIUM, HIGH, UNKNOWN;

    /** Converte o valor cru da IA. Só LOW/MEDIUM/HIGH são aceitos; qualquer outro é payload incoerente. */
    public static Optional<RiskLevel> fromAi(String raw) {
        if (raw == null) return Optional.empty();
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> Optional.of(LOW);
            case "MEDIUM" -> Optional.of(MEDIUM);
            case "HIGH" -> Optional.of(HIGH);
            default -> Optional.empty();
        };
    }

    /** Converte a coluna String de {@code flood_predictions}; valor desconhecido vira UNKNOWN. */
    public static RiskLevel fromColumn(String raw) {
        if (raw == null) return UNKNOWN;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}

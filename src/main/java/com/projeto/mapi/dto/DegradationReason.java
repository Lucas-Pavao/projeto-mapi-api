package com.projeto.mapi.dto;

/**
 * Motivo pelo qual não há previsão válida (ADR 0007 §4). A mensagem é um texto fixo em português:
 * o texto de exceções internas vai só para o log, nunca para o cliente.
 */
public enum DegradationReason {
    INSUFFICIENT_DATA("Dados insuficientes para estimar o risco neste momento."),
    AI_TIMEOUT("O modelo de previsão demorou demais para responder."),
    AI_UNAVAILABLE("O modelo de previsão está indisponível no momento."),
    AI_ERROR("O modelo de previsão falhou ou devolveu uma resposta inválida.");

    private final String message;

    DegradationReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    /** Lê o motivo gravado na auditoria; valor ausente ou desconhecido vira AI_ERROR. */
    public static DegradationReason fromAudit(String raw) {
        if (raw == null) return AI_ERROR;
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AI_ERROR;
        }
    }
}

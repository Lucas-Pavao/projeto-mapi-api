package com.projeto.mapi.dto;

/**
 * Estado de validação do modelo que gerou a previsão (ADR 0007, Emenda 1).
 *
 * <p>{@code NOT_VALIDATED} é o padrão e o único valor emitido hoje: os artefatos atuais não têm
 * metadata e a avaliação pré-registrada F1 foi inconclusiva. {@link #VALIDATED} só pode ser emitido
 * depois de um ADR que cite uma avaliação pré-registrada positiva; até lá, mesmo que a IA envie
 * {@code VALIDATED}, {@link #fromAi(String)} rebaixa para {@code NOT_VALIDATED}.
 */
public enum ModelValidationStatus {
    NOT_VALIDATED,
    VALIDATED;

    /**
     * Trava de segurança. Mudar para {@code true} exige ADR com a avaliação pré-registrada positiva
     * (Emenda 1 do ADR 0007); não é configuração de ambiente.
     */
    static final boolean VALIDATED_PERMITTED = false;

    /** Interpreta {@code model_validation_status} da IA: ausente, desconhecido ou não autorizado = NOT_VALIDATED. */
    public static ModelValidationStatus fromAi(String raw) {
        if (raw == null) return NOT_VALIDATED;
        if (VALIDATED.name().equalsIgnoreCase(raw.trim()) && VALIDATED_PERMITTED) return VALIDATED;
        return NOT_VALIDATED;
    }
}

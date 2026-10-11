package com.projeto.mapi.dto;

/**
 * Resultado de um ciclo de coleta. {@code sourceUnavailable} separa "a fonte não respondeu / todas
 * as chamadas falharam" de "a fonte respondeu e havia 0 leituras novas" — antes os dois viravam
 * 0/0 e uma queda total da APAC/ANA parecia um ciclo bem-sucedido (e o Retry nunca disparava).
 */
public record CollectionSummaryDTO(int processed, int errors, boolean sourceUnavailable) {

    /** Compatível com os chamadores antigos: ciclo em que a fonte respondeu. */
    public CollectionSummaryDTO(int processed, int errors) {
        this(processed, errors, false);
    }

    /** Fonte respondeu, nada a processar (ex.: coletor desativado). */
    public static CollectionSummaryDTO empty() {
        return new CollectionSummaryDTO(0, 0, false);
    }

    /** Fonte indisponível neste ciclo. */
    public static CollectionSummaryDTO unavailable() {
        return new CollectionSummaryDTO(0, 0, true);
    }
}

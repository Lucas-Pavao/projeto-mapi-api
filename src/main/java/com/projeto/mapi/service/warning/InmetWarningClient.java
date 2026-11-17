package com.projeto.mapi.service.warning;

/** Cliente HTTP da API pública de avisos ativos do INMET (ADR 0024). */
public interface InmetWarningClient {

    /** Baixa o JSON bruto de {@code /avisos/ativos}. Lança {@code SourceUnavailableException} em falha. */
    String fetchActiveJson();
}

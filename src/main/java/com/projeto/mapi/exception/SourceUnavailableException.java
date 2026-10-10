package com.projeto.mapi.exception;

/**
 * Fonte externa (ANA, APAC...) indisponível ou respondendo algo inutilizável (corpo vazio, JSON/XML
 * inválido, sem token). Existe para a falha NÃO se disfarçar de "0 leituras": quem coleta distingue
 * "a fonte caiu" (esta exceção, ou summary.sourceUnavailable) de "a fonte respondeu e não havia nada".
 */
public class SourceUnavailableException extends RuntimeException {
    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

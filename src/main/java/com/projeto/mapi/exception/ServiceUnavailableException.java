package com.projeto.mapi.exception;

/** Dependência necessária ao endpoint fora do ar ou inconsistente (HTTP 503). */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

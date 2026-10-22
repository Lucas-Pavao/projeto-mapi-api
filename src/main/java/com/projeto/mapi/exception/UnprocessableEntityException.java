package com.projeto.mapi.exception;

/** Entrada bem formada, mas fora do domínio aceito (HTTP 422). */
public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}

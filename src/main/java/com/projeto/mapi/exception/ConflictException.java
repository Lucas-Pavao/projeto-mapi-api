package com.projeto.mapi.exception;

/** Estado do recurso impede a operação (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

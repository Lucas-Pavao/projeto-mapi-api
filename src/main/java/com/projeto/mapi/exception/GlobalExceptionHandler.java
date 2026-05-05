package com.projeto.mapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Date;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleTokenException(TokenException ex, WebRequest request) {
        return new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorMessage handleUserAlreadyExistsException(UserAlreadyExistsException ex, WebRequest request) {
        return new ErrorMessage(
                HttpStatus.CONFLICT.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorMessage handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        return new ErrorMessage(
                HttpStatus.UNAUTHORIZED.value(),
                new Date(),
                "Invalid username or password",
                request.getDescription(false));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorMessage handleGlobalException(Exception ex, WebRequest request) {
        return new ErrorMessage(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }
}

package com.projeto.mapi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Date;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validação falhou em {}: {}", request.getDescription(false), message);
        return new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                new Date(),
                message.isBlank() ? "Payload inválido" : message,
                request.getDescription(false));
    }

    @ExceptionHandler(TokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleTokenException(TokenException ex, WebRequest request) {
        log.warn("Token inválido em {}: {}", request.getDescription(false), ex.getMessage());
        return new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorMessage handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Recurso não encontrado em {}: {}", request.getDescription(false), ex.getMessage());
        return new ErrorMessage(
                HttpStatus.NOT_FOUND.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleInvalidRequestException(InvalidRequestException ex, WebRequest request) {
        log.warn("Requisição inválida em {}: {}", request.getDescription(false), ex.getMessage());
        return new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }

    @ExceptionHandler({
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessage handleMissingOrInvalidParameter(Exception ex, WebRequest request) {
        log.warn("Parâmetro ausente ou inválido em {}: {}", request.getDescription(false), ex.getMessage());
        return new ErrorMessage(
                HttpStatus.BAD_REQUEST.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorMessage handleUserAlreadyExistsException(UserAlreadyExistsException ex, WebRequest request) {
        log.warn("Usuário já existe em {}: {}", request.getDescription(false), ex.getMessage());
        return new ErrorMessage(
                HttpStatus.CONFLICT.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorMessage handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        log.warn("Credenciais inválidas em {}", request.getDescription(false));
        return new ErrorMessage(
                HttpStatus.UNAUTHORIZED.value(),
                new Date(),
                "Invalid username or password",
                request.getDescription(false));
    }

    // Sem isso, qualquer URL sem rota mapeada (ex: um typo, ou um endpoint desativado) caía no
    // catch-all de Exception.class abaixo e virava 500 — descoberto ao testar o Actuator na porta
    // de management (uma URL inexistente devia dar 404, não erro interno).
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorMessage handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        log.warn("Rota inexistente: {}", request.getDescription(false));
        return new ErrorMessage(
                HttpStatus.NOT_FOUND.value(),
                new Date(),
                "Recurso não encontrado",
                request.getDescription(false));
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorMessage handleConflict(ConflictException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorMessage handleUnprocessable(UnprocessableEntityException ex, WebRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorMessage handleServiceUnavailable(ServiceUnavailableException ex, WebRequest request) {
        log.warn("Serviço indisponível em {}: {}", request.getDescription(false), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public org.springframework.http.ResponseEntity<ErrorMessage> handleTooManyRequests(TooManyRequestsException ex, WebRequest request) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request));
    }

    // @PreAuthorize lança AccessDeniedException dentro do controller; sem este handler o catch-all abaixo a
    // transformaria em 500.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorMessage handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "Perfil insuficiente para esta operação", request);
    }

    // Conta desativada (ADR 0010): mesma resposta de credencial inválida, para não revelar o estado da conta.
    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorMessage handleDisabled(org.springframework.security.authentication.DisabledException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    private static ErrorMessage build(HttpStatus status, String message, WebRequest request) {
        return new ErrorMessage(status.value(), new Date(), message, request.getDescription(false));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorMessage handleGlobalException(Exception ex, WebRequest request) {
        log.error("Erro não tratado em {}", request.getDescription(false), ex);
        return new ErrorMessage(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                new Date(),
                ex.getMessage(),
                request.getDescription(false));
    }
}

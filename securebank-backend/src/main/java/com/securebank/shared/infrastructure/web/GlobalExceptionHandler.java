package com.securebank.shared.infrastructure.web;

import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduz toda falha para o {@link ErrorResponse} único. Mensagens de domínio/aplicação são escritas para o cliente;
 * qualquer outra exceção vira 500 genérico (o detalhe vai só para o log, junto do traceId).
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> domain(DomainException e, HttpServletRequest request) {
        // INVALID_VALUE = entrada malformada (400); as demais são regras de negócio violadas (422).
        HttpStatus status = "INVALID_VALUE".equals(e.code()) ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        return body(status, e.code(), e.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<Object> application(ApplicationException e, HttpServletRequest request) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return body(status, e.code(), e.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> concurrentUpdate(OptimisticLockingFailureException e, HttpServletRequest request) {
        return body(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "The resource was modified by another request; retry",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with the current state",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error", request.getRequestURI(), null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map((FieldError f) -> new ErrorResponse.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", path(request), violations);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", path(request), null);
    }

    /** Demais erros padrão do Spring MVC (JSON malformado, parâmetro ausente, método/mídia não suportados, 404...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String code = status.is4xxClientError() ? status.name() : "INTERNAL_ERROR";
        String message = status.is4xxClientError() ? status.getReasonPhrase() : "Internal error";
        return body(status, code, message, path(request), null);
    }

    private static ResponseEntity<Object> body(HttpStatus status, String code, String message, String path,
            List<ErrorResponse.FieldViolation> violations) {
        ErrorResponse error = new ErrorResponse(Instant.now(), status.value(), code, message, path,
                MDC.get(TraceIdFilter.MDC_KEY), violations);
        return ResponseEntity.status(status).body(error);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}

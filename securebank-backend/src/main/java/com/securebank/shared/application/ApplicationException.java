package com.securebank.shared.application;

import java.time.Duration;

/**
 * Falha de caso de uso que não é regra de domínio: recurso inexistente (ou de outro dono — não revelamos qual),
 * chamador não identificado ou sem permissão, conflito com o estado atual, excesso de tentativas.
 * A camada web traduz {@link Kind} em HTTP.
 */
public class ApplicationException extends RuntimeException {

    public enum Kind { NOT_FOUND, UNAUTHENTICATED, FORBIDDEN, CONFLICT, UNPROCESSABLE, TOO_MANY_REQUESTS }

    private final Kind kind;
    private final String code;
    private final long retryAfterSeconds;

    private ApplicationException(Kind kind, String code, String message, long retryAfterSeconds) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApplicationException notFound(String what) {
        return new ApplicationException(Kind.NOT_FOUND, "NOT_FOUND", what + " not found", 0);
    }

    public static ApplicationException unauthenticated() {
        return unauthenticated("UNAUTHENTICATED", "Authentication required");
    }

    public static ApplicationException unauthenticated(String code, String message) {
        return new ApplicationException(Kind.UNAUTHENTICATED, code, message, 0);
    }

    public static ApplicationException forbidden() {
        return new ApplicationException(Kind.FORBIDDEN, "FORBIDDEN", "Access denied", 0);
    }

    public static ApplicationException conflict(String code, String message) {
        return new ApplicationException(Kind.CONFLICT, code, message, 0);
    }

    public static ApplicationException unprocessable(String code, String message) {
        return new ApplicationException(Kind.UNPROCESSABLE, code, message, 0);
    }

    public static ApplicationException tooManyRequests(String code, String message, Duration retryAfter) {
        return new ApplicationException(Kind.TOO_MANY_REQUESTS, code, message, Math.max(1, retryAfter.toSeconds()));
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

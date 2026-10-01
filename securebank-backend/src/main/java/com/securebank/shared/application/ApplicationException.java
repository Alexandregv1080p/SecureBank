package com.securebank.shared.application;

/**
 * Falha de caso de uso que não é regra de domínio: recurso inexistente (ou de outro dono — não revelamos qual),
 * chamador não identificado, conflito com o estado atual. A camada web traduz {@link Kind} em HTTP.
 */
public class ApplicationException extends RuntimeException {

    public enum Kind { NOT_FOUND, UNAUTHENTICATED, CONFLICT }

    private final Kind kind;
    private final String code;

    private ApplicationException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public static ApplicationException notFound(String what) {
        return new ApplicationException(Kind.NOT_FOUND, "NOT_FOUND", what + " not found");
    }

    public static ApplicationException unauthenticated() {
        return new ApplicationException(Kind.UNAUTHENTICATED, "UNAUTHENTICATED", "Authentication required");
    }

    public static ApplicationException conflict(String code, String message) {
        return new ApplicationException(Kind.CONFLICT, code, message);
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}

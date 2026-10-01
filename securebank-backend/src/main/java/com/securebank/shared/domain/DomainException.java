package com.securebank.shared.domain;

/** Violação de regra de negócio. O {@code code} é estável e vira o "code" do erro da API (seção 32). */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

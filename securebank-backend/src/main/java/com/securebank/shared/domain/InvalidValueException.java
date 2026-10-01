package com.securebank.shared.domain;

/** Valor que não respeita a invariante de um Value Object ou de um argumento de operação. */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String message) {
        super("INVALID_VALUE", message);
    }
}

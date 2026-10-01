package com.securebank.account.domain;

import com.securebank.shared.domain.InvalidValueException;

/** Agência: 4 dígitos. */
public record Branch(String value) {

    public Branch {
        if (value == null || !value.matches("\\d{4}")) {
            throw new InvalidValueException("Branch must have 4 digits");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

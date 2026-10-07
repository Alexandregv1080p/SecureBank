package com.securebank.shared.domain;

import java.util.UUID;

public record InvestmentId(UUID value) {

    public InvestmentId {
        if (value == null) {
            throw new InvalidValueException("InvestmentId is required");
        }
    }

    public static InvestmentId newId() {
        return new InvestmentId(UUID.randomUUID());
    }

    public static InvestmentId of(String value) {
        try {
            return new InvestmentId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid InvestmentId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

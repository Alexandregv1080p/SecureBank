package com.securebank.shared.domain;

import java.util.UUID;

public record PiggyId(UUID value) {

    public PiggyId {
        if (value == null) {
            throw new InvalidValueException("PiggyId is required");
        }
    }

    public static PiggyId newId() {
        return new PiggyId(UUID.randomUUID());
    }

    public static PiggyId of(String value) {
        try {
            return new PiggyId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid PiggyId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

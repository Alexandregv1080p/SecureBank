package com.securebank.shared.domain;

import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        if (value == null) {
            throw new InvalidValueException("AccountId is required");
        }
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(String value) {
        try {
            return new AccountId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid AccountId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

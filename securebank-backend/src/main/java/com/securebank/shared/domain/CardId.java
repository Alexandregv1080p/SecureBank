package com.securebank.shared.domain;

import java.util.UUID;

public record CardId(UUID value) {

    public CardId {
        if (value == null) {
            throw new InvalidValueException("CardId is required");
        }
    }

    public static CardId newId() {
        return new CardId(UUID.randomUUID());
    }

    public static CardId of(String value) {
        try {
            return new CardId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("Invalid CardId");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

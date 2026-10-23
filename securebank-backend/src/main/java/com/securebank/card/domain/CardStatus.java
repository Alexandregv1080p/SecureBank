package com.securebank.card.domain;

public enum CardStatus {
    ACTIVE, BLOCKED, CANCELED;

    public boolean canTransitionTo(CardStatus target) {
        return switch (this) {
            case ACTIVE -> target == BLOCKED || target == CANCELED;
            case BLOCKED -> target == ACTIVE || target == CANCELED;
            case CANCELED -> false;
        };
    }
}

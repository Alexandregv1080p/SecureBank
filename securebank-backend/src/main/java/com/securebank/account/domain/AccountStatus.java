package com.securebank.account.domain;

public enum AccountStatus {
    ACTIVE, BLOCKED, CLOSED;

    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case ACTIVE -> target == BLOCKED || target == CLOSED;
            case BLOCKED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}

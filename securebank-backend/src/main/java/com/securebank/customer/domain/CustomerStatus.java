package com.securebank.customer.domain;

public enum CustomerStatus {
    ACTIVE, BLOCKED, SUSPENDED, CLOSED;

    public boolean canTransitionTo(CustomerStatus target) {
        return switch (this) {
            case ACTIVE -> target == BLOCKED || target == SUSPENDED || target == CLOSED;
            case BLOCKED, SUSPENDED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}

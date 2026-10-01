package com.securebank.payment.domain;

public enum PaymentStatus {
    PENDING, PROCESSING, COMPLETED, FAILED;

    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING || target == FAILED;
            case PROCESSING -> target == COMPLETED || target == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}

package com.securebank.transaction.domain;

public enum TransactionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED, REVERSED;

    public boolean canTransitionTo(TransactionStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING || target == FAILED;
            case PROCESSING -> target == COMPLETED || target == FAILED;
            case COMPLETED -> target == REVERSED;
            case FAILED, REVERSED -> false;
        };
    }
}

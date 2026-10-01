package com.securebank.transfer.domain;

public enum TransferStatus {
    PENDING, COMPLETED, FAILED;

    public boolean canTransitionTo(TransferStatus target) {
        return this == PENDING && (target == COMPLETED || target == FAILED);
    }
}

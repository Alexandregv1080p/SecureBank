package com.securebank.limit.domain;

import com.securebank.transaction.domain.TransactionType;

public enum LimitType {
    WITHDRAW(TransactionType.WITHDRAW),
    TRANSFER(TransactionType.TRANSFER),
    PAYMENT(TransactionType.PAYMENT),
    PIX(TransactionType.PIX_OUT),
    FX(TransactionType.FX_BUY);

    private final TransactionType transactionType;

    LimitType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    /** Tipo de lançamento (débito) cujo total diário consome este limite. */
    public TransactionType transactionType() {
        return transactionType;
    }
}

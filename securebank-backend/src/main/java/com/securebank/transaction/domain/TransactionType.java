package com.securebank.transaction.domain;

public enum TransactionType {
    DEPOSIT, WITHDRAW, TRANSFER, PAYMENT, REFUND,
    /** Dinheiro guardado num porquinho (débito da conta) e resgatado dele (crédito). */
    PIGGY_IN, PIGGY_OUT
}

package com.securebank.transaction.domain;

/** Sentido do dinheiro em relação à conta do lançamento (o extrato precisa disso; TRANSFER sozinho é ambíguo). */
public enum TransactionDirection {
    CREDIT, DEBIT
}

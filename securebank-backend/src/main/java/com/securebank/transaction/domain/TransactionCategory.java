package com.securebank.transaction.domain;

import java.util.Arrays;
import java.util.List;

/** Agrupamento dos lançamentos para o cliente entender para onde o dinheiro vai; derivado do tipo (nada é gravado). */
public enum TransactionCategory {
    CASH, TRANSFERS, PAYMENTS, PIX, SAVINGS;

    public List<TransactionType> types() {
        return Arrays.stream(TransactionType.values()).filter(t -> t.category() == this).toList();
    }
}

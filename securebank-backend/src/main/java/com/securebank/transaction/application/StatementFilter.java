package com.securebank.transaction.application;

import com.securebank.transaction.domain.TransactionCategory;
import com.securebank.transaction.domain.TransactionDirection;

/** Filtros opcionais do extrato; nulo = sem filtro naquele campo. */
public record StatementFilter(TransactionCategory category, TransactionDirection direction) {

    public static final StatementFilter NONE = new StatementFilter(null, null);
}

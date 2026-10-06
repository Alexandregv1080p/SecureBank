package com.securebank.transaction.application;

import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionType;
import java.math.BigDecimal;

/** Soma dos lançamentos concluídos de um tipo e sentido num período. */
public record StatementTotal(TransactionType type, TransactionDirection direction, BigDecimal sum) {}

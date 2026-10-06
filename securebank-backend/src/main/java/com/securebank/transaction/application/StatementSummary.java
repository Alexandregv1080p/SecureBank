package com.securebank.transaction.application;

import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.TransactionCategory;
import java.time.YearMonth;
import java.util.List;

/** Resumo mensal: entradas, saídas e resultado do mês, e o mesmo por categoria (só lançamentos concluídos). */
public record StatementSummary(YearMonth month, Money income, Money expenses, List<ByCategory> byCategory) {

    public record ByCategory(TransactionCategory category, Money income, Money expenses) {}

    public Money net() {
        return income.minus(expenses);
    }
}

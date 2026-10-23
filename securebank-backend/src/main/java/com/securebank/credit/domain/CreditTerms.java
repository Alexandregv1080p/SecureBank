package com.securebank.credit.domain;

import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Condições do cartão de crédito: limite e dia de vencimento. A fatura de um mês vence no dia escolhido e fecha
 * {@value #DAYS_TO_PAY} dias antes; compras até o dia do fechamento (inclusive) entram na fatura que fecha ali.
 */
public final class CreditTerms {

    public static final int DAYS_TO_PAY = 7;
    public static final List<Integer> DUE_DAYS = List.of(5, 10, 15, 20, 25);
    public static final Money DEFAULT_LIMIT = Money.brl("2000.00");

    private final CardId cardId;
    private final Money limit;
    private final int dueDay;

    private CreditTerms(CardId cardId, Money limit, int dueDay) {
        this.cardId = cardId;
        this.limit = limit;
        this.dueDay = dueDay;
    }

    public static CreditTerms grant(CardId cardId, int dueDay) {
        if (cardId == null) {
            throw new InvalidValueException("Credit terms require a card");
        }
        if (!DUE_DAYS.contains(dueDay)) {
            throw new InvalidValueException("The due day must be one of " + DUE_DAYS);
        }
        return new CreditTerms(cardId, DEFAULT_LIMIT, dueDay);
    }

    /** Reconstitui condições já persistidas. */
    public static CreditTerms restore(CardId cardId, Money limit, int dueDay) {
        return new CreditTerms(cardId, limit, dueDay);
    }

    public LocalDate dueDate(YearMonth month) {
        return month.atDay(dueDay);
    }

    public LocalDate closingDate(YearMonth month) {
        return dueDate(month).minusDays(DAYS_TO_PAY);
    }

    /** Em qual fatura cai uma compra feita em [date]: a primeira cujo fechamento ainda não passou. */
    public YearMonth invoiceFor(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        while (closingDate(month).isBefore(date)) {
            month = month.plusMonths(1);
        }
        return month;
    }

    public CardId cardId() { return cardId; }
    public Money limit() { return limit; }
    public int dueDay() { return dueDay; }
}

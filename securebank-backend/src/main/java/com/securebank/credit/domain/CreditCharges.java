package com.securebank.credit.domain;

import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Encargos de uma fatura vencida (simulados): multa única de 2% sobre o principal que ficou sem pagar, e juros de 10% ao
 * mês calculados por dia (10% ÷ 30 por dia) sobre tudo o que falta pagar, com os juros dos dias anteriores incluídos.
 */
public final class CreditCharges {

    public static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.02");
    public static final BigDecimal MONTHLY_INTEREST = new BigDecimal("0.10");
    public static final int DAYS_IN_MONTH = 30;

    private CreditCharges() {}

    /** Multa por não pagar o mínimo: 2% do principal ainda em aberto, arredondado ao centavo. */
    public static Money lateFee(Money unpaidPrincipal) {
        return new Money(unpaidPrincipal.amount().multiply(LATE_FEE_RATE).setScale(2, RoundingMode.HALF_UP), unpaidPrincipal.currency());
    }

    /** Juros de um dia sobre [remaining]; zero quando não chega a um centavo. */
    public static Money dailyInterest(Money remaining) {
        BigDecimal interest = remaining.amount().multiply(MONTHLY_INTEREST).divide(BigDecimal.valueOf(DAYS_IN_MONTH), 2, RoundingMode.HALF_UP);
        return new Money(interest, remaining.currency());
    }
}

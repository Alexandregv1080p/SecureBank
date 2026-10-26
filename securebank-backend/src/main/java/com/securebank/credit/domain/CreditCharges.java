package com.securebank.credit.domain;

import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Encargos de uma fatura vencida (simulados): multa única de 2% sobre o principal que ficou sem pagar, e juros de 10% ao
 * mês calculados por dia (10% ÷ 30 por dia) sobre tudo o que falta pagar, com os juros dos dias anteriores incluídos.
 * Teto: multa e juros somados nunca passam de 100% do valor original da fatura (o principal), como no rotativo real.
 */
public final class CreditCharges {

    public static final BigDecimal LATE_FEE_RATE = new BigDecimal("0.02");
    public static final BigDecimal MONTHLY_INTEREST = new BigDecimal("0.10");
    public static final int DAYS_IN_MONTH = 30;

    private CreditCharges() {}

    /** Quanto de encargo ainda cabe numa fatura: o principal menos o que já foi cobrado (nunca negativo). */
    public static Money room(Money principal, Money chargedSoFar) {
        Money room = principal.minus(chargedSoFar);
        return room.isPositive() ? room : Money.zero(principal.currency());
    }

    /** [charge] limitado ao que cabe sob o teto. */
    public static Money capped(Money charge, Money room) {
        return charge.isGreaterThan(room) ? room : charge;
    }

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

package com.securebank.credit.domain;

import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;

/**
 * Mudança de limite. O cliente diminui à vontade (nunca abaixo do que deve) e aumenta sozinho até R$ 10.000,00 se não há
 * fatura vencida sem o mínimo; acima disso só a equipe, até R$ 100.000,00. O limite é sempre múltiplo de R$ 100,00.
 */
public final class CreditLimits {

    public static final Money MIN = Money.brl("100.00");
    public static final Money SELF_SERVICE_MAX = Money.brl("10000.00");
    public static final Money STAFF_MAX = Money.brl("100000.00");
    private static final BigDecimal STEP = new BigDecimal("100");

    private CreditLimits() {}

    /** Valor válido em qualquer caso: positivo, a partir de R$ 100,00 e em passos de R$ 100,00. */
    public static void requireWellFormed(Money limit) {
        if (limit == null || limit.compareTo(MIN) < 0) {
            throw new InvalidValueException("The limit must be at least R$ " + MIN.amount().toPlainString());
        }
        if (limit.amount().remainder(STEP).signum() != 0) {
            throw new InvalidValueException("The limit must be a multiple of R$ 100.00");
        }
    }

    /** Pedido do próprio cliente. */
    public static void checkSelfService(CreditTerms terms, Money newLimit, Money used, boolean hasBlockingInvoice) {
        requireWellFormed(newLimit);
        requireNotBelowUsed(newLimit, used);
        if (newLimit.isGreaterThan(terms.limit())) {
            if (hasBlockingInvoice) {
                throw CreditRefusedException.overdue();
            }
            if (newLimit.isGreaterThan(SELF_SERVICE_MAX)) {
                throw CreditRefusedException.limitAboveSelfService();
            }
        }
    }

    /** Decisão da equipe: até R$ 100.000,00, nunca abaixo da dívida. */
    public static void checkStaff(Money newLimit, Money used) {
        requireWellFormed(newLimit);
        requireNotBelowUsed(newLimit, used);
        if (newLimit.isGreaterThan(STAFF_MAX)) {
            throw new InvalidValueException("The limit cannot be more than R$ " + STAFF_MAX.amount().toPlainString());
        }
    }

    private static void requireNotBelowUsed(Money newLimit, Money used) {
        if (newLimit.isLessThan(used)) {
            throw CreditRefusedException.limitBelowUsed();
        }
    }
}

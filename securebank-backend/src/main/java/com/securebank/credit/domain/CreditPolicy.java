package com.securebank.credit.domain;

import com.securebank.shared.domain.Money;

/** Regras de aprovação de uma compra no crédito. */
public final class CreditPolicy {

    private CreditPolicy() {}

    /**
     * @param used o que ainda está devendo: todas as parcelas (de faturas abertas e futuras) menos o que já foi pago
     * @param hasOverdue há fatura vencida com saldo: nada novo até quitar
     */
    public static void ensureCanBuy(CreditTerms terms, Money used, Money total, boolean hasOverdue) {
        if (hasOverdue) {
            throw CreditRefusedException.overdue();
        }
        if (total.isGreaterThan(available(terms, used))) {
            throw CreditRefusedException.limitExceeded();
        }
    }

    /** Limite que ainda pode ser usado (nunca negativo). */
    public static Money available(CreditTerms terms, Money used) {
        Money left = terms.limit().minus(used);
        return left.isPositive() ? left : Money.zero(left.currency());
    }
}

package com.securebank.credit.domain;

import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Parcelamento da fatura: o que falta pagar vira de 2 a {@value #MAX_INSTALLMENTS} parcelas fixas (tabela Price) com juros
 * de 5% ao mês, nas próximas faturas. Só vale para fatura já fechada, com pelo menos R$ 50,00 a pagar, e parcela mínima de
 * R$ 10,00. A fatura parcelada fica quitada (sai o atraso, param os encargos) e a dívida nova usa o limite.
 */
public final class Refinance {

    public static final int MAX_INSTALLMENTS = 12;
    public static final BigDecimal MONTHLY_RATE = new BigDecimal("0.05");
    public static final Money MIN_REMAINING = Money.brl("50.00");
    public static final Money MIN_INSTALLMENT = Money.brl("10.00");

    /** Uma opção: [installments] parcelas de [installment], [total] ao fim, dos quais [interest] são juros. */
    public record Plan(int installments, Money installment, Money total, Money interest) {}

    private Refinance() {}

    /** Só se parcela fatura fechada (ou vencida) com saldo suficiente. */
    public static void requireRefinanceable(Invoice invoice) {
        boolean closed = invoice.status() == InvoiceStatus.CLOSED || invoice.status() == InvoiceStatus.OVERDUE
                || invoice.status() == InvoiceStatus.REVOLVING;
        if (!closed) {
            throw new InvoiceNotPayableException("Only a closed invoice can be paid in installments");
        }
        if (invoice.remaining().isLessThan(MIN_REMAINING)) {
            throw new InvoiceNotPayableException("An invoice needs at least R$ " + MIN_REMAINING.amount().toPlainString() + " left to be paid in installments");
        }
    }

    public static Plan plan(Money remaining, int installments) {
        if (installments < 2 || installments > MAX_INSTALLMENTS) {
            throw new InvalidValueException("Installments must be between 2 and " + MAX_INSTALLMENTS);
        }
        MathContext mc = new MathContext(20, RoundingMode.HALF_EVEN);
        BigDecimal growth = BigDecimal.ONE.add(MONTHLY_RATE).pow(installments, mc);
        BigDecimal factor = MONTHLY_RATE.multiply(growth, mc).divide(growth.subtract(BigDecimal.ONE), mc);
        Money each = new Money(remaining.amount().multiply(factor, mc).setScale(2, RoundingMode.HALF_UP), remaining.currency());
        if (each.isLessThan(MIN_INSTALLMENT)) {
            throw new InvalidValueException("Each installment must be at least R$ " + MIN_INSTALLMENT.amount().toPlainString());
        }
        Money total = new Money(each.amount().multiply(BigDecimal.valueOf(installments)), remaining.currency());
        return new Plan(installments, each, total, total.minus(remaining));
    }

    /** Todas as opções que valem para o saldo (de 2x até onde a parcela mínima permite). */
    public static List<Plan> options(Money remaining) {
        List<Plan> plans = new ArrayList<>();
        for (int n = 2; n <= MAX_INSTALLMENTS; n++) {
            try {
                plans.add(plan(remaining, n));
            } catch (InvalidValueException tooSmall) {
                break; // quanto mais parcelas, menor a parcela: as seguintes também não valem
            }
        }
        return plans;
    }
}

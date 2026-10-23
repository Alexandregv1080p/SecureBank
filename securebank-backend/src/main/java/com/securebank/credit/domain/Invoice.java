package com.securebank.credit.domain;

import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Fatura de um mês, calculada das parcelas ([principal]), dos encargos por atraso ([charges]) e dos pagamentos: nada fica
 * gravado como "estado". O pagamento mínimo é 15% do principal (no mínimo R$ 20,00, ou o principal todo se for menor).
 * Depois do vencimento: com o mínimo pago a fatura segue no rotativo (juros sobre o que sobrou); sem o mínimo ela fica
 * vencida e bloqueia novas compras.
 */
public record Invoice(YearMonth month, LocalDate closingDate, LocalDate dueDate, Money principal, Money charges, Money paid,
        InvoiceStatus status) {

    public static final BigDecimal MINIMUM_PERCENT = new BigDecimal("0.15");
    public static final Money MINIMUM_FLOOR = Money.brl("20.00");

    public static Invoice of(CreditTerms terms, YearMonth month, Money principal, Money charges, Money paid, LocalDate today) {
        LocalDate closing = terms.closingDate(month);
        LocalDate due = terms.dueDate(month);
        Money total = principal.plus(charges);
        Money remaining = total.minus(paid);
        InvoiceStatus status;
        if (total.isPositive() && !remaining.isPositive()) {
            status = InvoiceStatus.PAID;
        } else if (remaining.isPositive() && today.isAfter(due)) {
            status = paid.compareTo(minimumOf(principal)) >= 0 ? InvoiceStatus.REVOLVING : InvoiceStatus.OVERDUE;
        } else if (today.isAfter(closing)) {
            status = InvoiceStatus.CLOSED;
        } else {
            status = InvoiceStatus.OPEN;
        }
        return new Invoice(month, closing, due, principal, charges, paid, status);
    }

    /** O pagamento mínimo de uma fatura de [principal]: 15%, no mínimo R$ 20,00 e nunca mais que o próprio principal. */
    public static Money minimumOf(Money principal) {
        if (!principal.isPositive()) {
            return Money.zero(principal.currency());
        }
        Money percent = new Money(principal.amount().multiply(MINIMUM_PERCENT).setScale(2, RoundingMode.HALF_UP), principal.currency());
        Money wanted = percent.isLessThan(MINIMUM_FLOOR) ? MINIMUM_FLOOR : percent;
        return wanted.isGreaterThan(principal) ? principal : wanted;
    }

    public Money total() {
        return principal.plus(charges);
    }

    public Money remaining() {
        Money remaining = total().minus(paid);
        return remaining.isPositive() ? remaining : Money.zero(principal.currency());
    }

    public Money minimum() {
        return minimumOf(principal);
    }

    /** Quanto ainda falta pagar para cumprir o mínimo (zero se já cumpriu ou se a fatura foi quitada). */
    public Money minimumDue() {
        Money due = minimum().minus(paid);
        Money capped = due.isGreaterThan(remaining()) ? remaining() : due;
        return capped.isPositive() ? capped : Money.zero(principal.currency());
    }

    /** Fatura vencida sem o mínimo pago: nenhuma compra nova até regularizar. */
    public boolean blocksPurchases() {
        return status == InvoiceStatus.OVERDUE;
    }

    /** Pagamento parcial ou total: positivo e no máximo o que falta. */
    public void ensureCanPay(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        if (!remaining().isPositive()) {
            throw new InvoiceNotPayableException("This invoice has nothing left to pay");
        }
        if (amount.isGreaterThan(remaining())) {
            throw new InvoiceNotPayableException("The amount is more than what is left on the invoice");
        }
    }
}

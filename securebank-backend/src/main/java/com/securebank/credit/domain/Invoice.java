package com.securebank.credit.domain;

import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Fatura de um mês, calculada a partir das parcelas e dos pagamentos (nada fica gravado como "estado"): total, o que já foi
 * pago e a situação do dia. Pagar menos que o total é permitido; o que sobra continua na fatura e, vencida, ela bloqueia
 * novas compras (não há juros nem rotativo nesta simulação).
 */
public record Invoice(YearMonth month, LocalDate closingDate, LocalDate dueDate, Money total, Money paid, InvoiceStatus status) {

    public static Invoice of(CreditTerms terms, YearMonth month, Money total, Money paid, LocalDate today) {
        LocalDate closing = terms.closingDate(month);
        LocalDate due = terms.dueDate(month);
        Money remaining = total.minus(paid);
        InvoiceStatus status;
        if (total.isPositive() && !remaining.isPositive()) {
            status = InvoiceStatus.PAID;
        } else if (remaining.isPositive() && today.isAfter(due)) {
            status = InvoiceStatus.OVERDUE;
        } else if (today.isAfter(closing)) {
            status = InvoiceStatus.CLOSED;
        } else {
            status = InvoiceStatus.OPEN;
        }
        return new Invoice(month, closing, due, total, paid, status);
    }

    public Money remaining() {
        Money remaining = total.minus(paid);
        return remaining.isPositive() ? remaining : Money.zero(total.currency());
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

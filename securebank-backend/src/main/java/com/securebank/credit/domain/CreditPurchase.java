package com.securebank.credit.domain;

import com.securebank.card.domain.CardPurchase;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compra no crédito, à vista ou em até {@value #MAX_INSTALLMENTS} parcelas sem juros. Cada parcela cai numa fatura (a primeira
 * na fatura que ainda aceita compras no dia, as outras nos meses seguintes) e usa limite até ser paga.
 */
public final class CreditPurchase {

    public static final int MAX_INSTALLMENTS = 12;
    public static final Money MIN_INSTALLMENT = Money.brl("5.00");

    /** Uma parcela: número (1..n), valor e a fatura em que cai. */
    public record Installment(UUID id, int number, Money amount, YearMonth invoiceMonth) {}

    private final UUID id;
    private final CardId cardId;
    private final String merchant;
    private final Money total;
    private final int installments;
    private final Instant createdAt;
    private final List<Installment> plan;

    private CreditPurchase(UUID id, CardId cardId, String merchant, Money total, int installments, Instant createdAt,
            List<Installment> plan) {
        this.id = id;
        this.cardId = cardId;
        this.merchant = merchant;
        this.total = total;
        this.installments = installments;
        this.createdAt = createdAt;
        this.plan = List.copyOf(plan);
    }

    public static CreditPurchase create(CreditTerms terms, Money total, String merchant, int installments, LocalDate today,
            Instant now) {
        if (terms == null || total == null || now == null || today == null) {
            throw new InvalidValueException("Purchase requires terms, amount and time");
        }
        if (!total.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        if (installments < 1 || installments > MAX_INSTALLMENTS) {
            throw new InvalidValueException("Installments must be between 1 and " + MAX_INSTALLMENTS);
        }
        String name = CardPurchase.validateMerchant(merchant);
        List<Money> amounts = split(total, installments);
        if (installments > 1 && amounts.get(0).isLessThan(MIN_INSTALLMENT)) {
            throw new InvalidValueException("Each installment must be at least R$ " + MIN_INSTALLMENT.amount().toPlainString());
        }
        YearMonth first = terms.invoiceFor(today);
        List<Installment> plan = new ArrayList<>();
        for (int i = 0; i < installments; i++) {
            plan.add(new Installment(UUID.randomUUID(), i + 1, amounts.get(i), first.plusMonths(i)));
        }
        return new CreditPurchase(UUID.randomUUID(), terms.cardId(), name, total, installments, now, plan);
    }

    public static CreditPurchase restore(UUID id, CardId cardId, String merchant, Money total, int installments, Instant createdAt) {
        return new CreditPurchase(id, cardId, merchant, total, installments, createdAt, List.of());
    }

    /** Divide em parcelas iguais de centavos inteiros; o que sobra da divisão vai para a última. */
    public static List<Money> split(Money total, int installments) {
        BigDecimal each = total.amount().divide(BigDecimal.valueOf(installments), 2, RoundingMode.DOWN);
        List<Money> parts = new ArrayList<>();
        Money sum = Money.zero(total.currency());
        for (int i = 0; i < installments - 1; i++) {
            Money part = new Money(each, total.currency());
            parts.add(part);
            sum = sum.plus(part);
        }
        parts.add(total.minus(sum));
        return parts;
    }

    public UUID id() { return id; }
    public CardId cardId() { return cardId; }
    public String merchant() { return merchant; }
    public Money total() { return total; }
    public int installments() { return installments; }
    public Instant createdAt() { return createdAt; }
    /** As parcelas planejadas (vazio numa compra reconstituída: elas ficam no repositório). */
    public List<Installment> plan() { return plan; }
}

package com.securebank.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Dinheiro: BigDecimal + moeda, nunca double/float. A escala é a da moeda (BRL = 2), então
 * {@code equals} funciona (1.5 == 1.50). Valor com casas a mais é rejeitado, nunca arredondado
 * em silêncio — arredondar dinheiro é uma decisão de negócio explícita (juros, tarifas), não um efeito colateral.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public static final Currency BRL = Currency.getInstance("BRL");

    public Money {
        if (amount == null || currency == null) {
            throw new InvalidValueException("Money requires an amount and a currency");
        }
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new InvalidValueException("Unsupported currency " + currency);
        }
        try {
            amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidValueException("Amount has more than " + digits + " decimal places");
        }
    }

    public static Money brl(String amount) {
        return new Money(new BigDecimal(amount), BRL);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    public void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}

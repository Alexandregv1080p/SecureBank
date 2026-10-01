package com.securebank.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesScaleSoEqualsWorks() {
        assertThat(new Money(new BigDecimal("1.5"), Money.BRL)).isEqualTo(Money.brl("1.50"));
        assertThat(new Money(new BigDecimal("10"), Money.BRL).amount().scale()).isEqualTo(2);
    }

    @Test
    void isExactWhereDoubleIsNot() {
        assertThat(Money.brl("0.10").plus(Money.brl("0.20"))).isEqualTo(Money.brl("0.30"));
        assertThat(0.1 + 0.2).isNotEqualTo(0.3); // o motivo de existir esta classe
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyInsteadOfRoundingSilently() {
        assertThatThrownBy(() -> Money.brl("10.001")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsMixedCurrencies() {
        Money usd = new Money(BigDecimal.ONE, Currency.getInstance("USD"));
        assertThatThrownBy(() -> Money.brl("1.00").plus(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Money.brl("1.00").compareTo(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void comparesAndSubtracts() {
        assertThat(Money.brl("5.00").isGreaterThan(Money.brl("4.99"))).isTrue();
        assertThat(Money.brl("5.00").minus(Money.brl("5.00")).isZero()).isTrue();
        assertThat(Money.zero(Money.BRL).isPositive()).isFalse();
    }

    @Test
    void requiresAmountAndCurrency() {
        assertThatThrownBy(() -> new Money(null, Money.BRL)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(InvalidValueException.class);
    }
}

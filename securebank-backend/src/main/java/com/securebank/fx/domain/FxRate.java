package com.securebank.fx.domain;

import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;

/**
 * Cotação simulada: [mid] é o valor de 1 unidade da moeda em reais; o cliente COMPRA pelo [ask] (mid + spread) e VENDE
 * pelo [bid] (mid − spread). O arredondamento sempre favorece o banco: compra arredonda o custo para cima, venda
 * arredonda o que se recebe para baixo.
 */
public record FxRate(Currency currency, BigDecimal mid, BigDecimal spread, Instant updatedAt) {

    public static final int RATE_SCALE = 6;

    public FxRate {
        if (currency == null || mid == null || spread == null || updatedAt == null || mid.signum() <= 0
                || spread.signum() < 0 || spread.compareTo(BigDecimal.ONE) >= 0) {
            throw new InvalidValueException("Invalid FX rate");
        }
    }

    /** Preço de compra do cliente (reais por unidade). */
    public BigDecimal ask() {
        return mid.multiply(BigDecimal.ONE.add(spread)).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Preço de venda do cliente (reais por unidade). */
    public BigDecimal bid() {
        return mid.multiply(BigDecimal.ONE.subtract(spread)).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Quanto custa, em reais, comprar [foreign] (para cima, ao centavo). */
    public Money buyCost(Money foreign) {
        requireCurrency(foreign);
        return new Money(foreign.amount().multiply(ask()).setScale(2, RoundingMode.UP), Money.BRL);
    }

    /** Quanto se recebe, em reais, vendendo [foreign] (para baixo, ao centavo); precisa dar pelo menos R$ 0,01. */
    public Money sellProceeds(Money foreign) {
        requireCurrency(foreign);
        Money proceeds = new Money(foreign.amount().multiply(bid()).setScale(2, RoundingMode.DOWN), Money.BRL);
        if (!proceeds.isPositive()) {
            throw new InvalidValueException("Amount is too small to sell");
        }
        return proceeds;
    }

    private void requireCurrency(Money foreign) {
        if (!foreign.currency().equals(currency) || !foreign.isPositive()) {
            throw new InvalidValueException("Amount must be positive and in " + currency.getCurrencyCode());
        }
    }
}

package com.securebank.fx.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class FxDomainTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final FxRate RATE = new FxRate(USD, new BigDecimal("5.2000"), new BigDecimal("0.0150"), NOW);

    private static Money usd(String v) {
        return new Money(new BigDecimal(v), USD);
    }

    @Test
    void theCustomerBuysAboveAndSellsBelowTheMidRate() {
        assertThat(RATE.ask()).isEqualByComparingTo("5.278000");
        assertThat(RATE.bid()).isEqualByComparingTo("5.122000");
        assertThat(RATE.ask().compareTo(RATE.mid())).isPositive();
        assertThat(RATE.bid().compareTo(RATE.mid())).isNegative();
    }

    @Test
    void buyingCostsTheAskRoundedUpToTheCent() {
        assertThat(RATE.buyCost(usd("100.00"))).isEqualTo(Money.brl("527.80"));
        assertThat(RATE.buyCost(usd("0.01"))).isEqualTo(Money.brl("0.06")); // 0.05278 → sobe para 0.06
        assertThat(RATE.buyCost(usd("1.00"))).isEqualTo(Money.brl("5.28"));
    }

    @Test
    void sellingPaysTheBidRoundedDownToTheCent() {
        assertThat(RATE.sellProceeds(usd("100.00"))).isEqualTo(Money.brl("512.20"));
        assertThat(RATE.sellProceeds(usd("1.00"))).isEqualTo(Money.brl("5.12"));
        assertThat(RATE.sellProceeds(usd("0.99"))).isEqualTo(Money.brl("5.07")); // 5.07078 → desce
    }

    @Test
    void aRoundTripAlwaysLosesToTheSpreadNeverGains() {
        for (String v : new String[] {"0.01", "1.00", "37.13", "100.00", "999.99"}) {
            Money cost = RATE.buyCost(usd(v));
            Money back = RATE.sellProceeds(usd(v));
            assertThat(back.isLessThan(cost)).as(v).isTrue();
        }
    }

    @Test
    void anAmountThatPaysLessThanOneCentCannotBeSold() {
        var cheap = new FxRate(USD, new BigDecimal("0.100000"), BigDecimal.ZERO, NOW);

        assertThatThrownBy(() -> cheap.sellProceeds(usd("0.01"))).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void ratesRejectNonsense() {
        assertThatThrownBy(() -> new FxRate(USD, BigDecimal.ZERO, new BigDecimal("0.01"), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new FxRate(USD, BigDecimal.ONE, new BigDecimal("-0.01"), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new FxRate(USD, BigDecimal.ONE, BigDecimal.ONE, NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void quotesOnlyAcceptTheirOwnCurrency() {
        assertThatThrownBy(() -> RATE.buyCost(new Money(new BigDecimal("10.00"), Currency.getInstance("EUR"))))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> RATE.buyCost(usd("0.00"))).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void aWalletCreditsAndDebitsButNeverGoesNegative() {
        var wallet = FxWallet.open(CustomerId.newId(), USD, NOW);

        wallet.credit(usd("50.00"), NOW);
        wallet.debit(usd("20.00"), NOW);

        assertThat(wallet.balance()).isEqualTo(usd("30.00"));
        assertThatThrownBy(() -> wallet.debit(usd("30.01"), NOW)).isInstanceOf(InsufficientFxFundsException.class);
        assertThat(wallet.balance()).isEqualTo(usd("30.00")); // a falha não mexe no saldo
        wallet.debit(usd("30.00"), NOW);
        assertThat(wallet.balance().isZero()).isTrue();
    }

    @Test
    void aWalletRejectsOtherCurrenciesAndNonPositiveAmounts() {
        var wallet = FxWallet.open(CustomerId.newId(), USD, NOW);

        assertThatThrownBy(() -> wallet.credit(new Money(new BigDecimal("1.00"), Currency.getInstance("EUR")), NOW))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> wallet.credit(usd("0.00"), NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> wallet.debit(usd("0.00"), NOW)).isInstanceOf(InvalidValueException.class);
    }
}

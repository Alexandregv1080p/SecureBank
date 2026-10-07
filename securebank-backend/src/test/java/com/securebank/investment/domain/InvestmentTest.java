package com.securebank.investment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvestmentTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");
    private static final CustomerId CUSTOMER = CustomerId.newId();
    private static final AccountId ACCOUNT = AccountId.newId();

    private static final InvestmentProduct DAILY = new InvestmentProduct("CDB_DAILY", "CDB Liquidez Diária",
            InvestmentKind.DAILY, new BigDecimal("0.1050"), null, new BigDecimal("1.00"), true);
    private static final InvestmentProduct TERM_90 = new InvestmentProduct("CDB_90", "CDB 90 dias",
            InvestmentKind.TERM, new BigDecimal("0.1150"), 90, new BigDecimal("100.00"), true);

    private static Instant plusDays(long days) {
        return T0.plus(Duration.ofDays(days));
    }

    private static Investment daily(String amount) {
        return Investment.apply(CUSTOMER, ACCOUNT, DAILY, Money.brl(amount), T0);
    }

    @Test
    void doesNotYieldBeforeAFullDayHasPassed() {
        var v = daily("1000.00").valuation(T0.plus(Duration.ofHours(23)));

        assertThat(v.days()).isZero();
        assertThat(v.gross()).isEqualTo(Money.brl("1000.00"));
        assertThat(v.tax()).isEqualTo(Money.brl("0.00"));
        assertThat(v.net()).isEqualTo(Money.brl("1000.00"));
    }

    @Test
    void aYearAtTheAnnualRateYieldsThatRateCompounded() {
        var v = daily("1000.00").valuation(plusDays(365));

        assertThat(v.days()).isEqualTo(365);
        assertThat(v.gross().amount()).isBetween(new BigDecimal("1104.99"), new BigDecimal("1105.01")); // 10,50% a.a.
        assertThat(v.yield().amount()).isBetween(new BigDecimal("104.99"), new BigDecimal("105.01"));
        assertThat(v.taxRate()).isEqualByComparingTo("0.175"); // 361 a 720 dias
        assertThat(v.net()).isEqualTo(v.gross().minus(v.tax()));
        assertThat(v.net().amount()).isBetween(new BigDecimal("1086.60"), new BigDecimal("1086.65"));
    }

    @Test
    void theYieldGrowsDayAfterDay() {
        var inv = daily("5000.00");
        var previous = inv.valuation(plusDays(0)).gross();
        for (int day = 1; day <= 60; day++) {
            var gross = inv.valuation(plusDays(day)).gross();
            assertThat(gross.isLessThan(previous)).as("day %d", day).isFalse();
            previous = gross;
        }
        assertThat(previous.isGreaterThan(Money.brl("5000.00"))).isTrue();
    }

    @Test
    void theIncomeTaxIsRegressive() {
        assertThat(Investment.taxRate(0)).isEqualByComparingTo("0.225");
        assertThat(Investment.taxRate(180)).isEqualByComparingTo("0.225");
        assertThat(Investment.taxRate(181)).isEqualByComparingTo("0.20");
        assertThat(Investment.taxRate(360)).isEqualByComparingTo("0.20");
        assertThat(Investment.taxRate(361)).isEqualByComparingTo("0.175");
        assertThat(Investment.taxRate(720)).isEqualByComparingTo("0.175");
        assertThat(Investment.taxRate(721)).isEqualByComparingTo("0.15");
    }

    @Test
    void aDailyLiquidityInvestmentRedeemsAnytimeAndPaysTheNet() {
        var inv = daily("1000.00");

        Money net = inv.redeem(plusDays(30));

        assertThat(inv.status()).isEqualTo(InvestmentStatus.REDEEMED);
        assertThat(net.isGreaterThan(Money.brl("1000.00"))).isTrue();
        assertThat(net).isEqualTo(inv.valuation(plusDays(999)).net()); // depois de resgatada, vale o que foi pago
        assertThat(inv.redeemedGross().minus(inv.redeemedTax())).isEqualTo(net);
    }

    @Test
    void aRedeemedInvestmentCannotBeRedeemedAgain() {
        var inv = daily("100.00");
        inv.redeem(plusDays(1));

        assertThatThrownBy(() -> inv.redeem(plusDays(2))).isInstanceOf(InvestmentAlreadyRedeemedException.class);
    }

    @Test
    void aTermInvestmentOnlyRedeemsAtMaturity() {
        var inv = Investment.apply(CUSTOMER, ACCOUNT, TERM_90, Money.brl("1000.00"), T0);

        assertThat(inv.maturesAt()).isEqualTo(plusDays(90));
        assertThat(inv.canRedeem(plusDays(89))).isFalse();
        assertThatThrownBy(() -> inv.redeem(T0.plus(Duration.ofDays(90)).minusSeconds(1)))
                .isInstanceOf(InvestmentNotMaturedException.class);
        assertThat(inv.status()).isEqualTo(InvestmentStatus.ACTIVE); // a falha não muda nada
        assertThat(inv.canRedeem(plusDays(90))).isTrue();
        assertThat(inv.redeem(plusDays(90)).isGreaterThan(Money.brl("1000.00"))).isTrue();
    }

    @Test
    void aTermInvestmentStopsYieldingAtMaturity() {
        var inv = Investment.apply(CUSTOMER, ACCOUNT, TERM_90, Money.brl("1000.00"), T0);

        assertThat(inv.valuation(plusDays(200)).days()).isEqualTo(90);
        assertThat(inv.valuation(plusDays(200)).gross()).isEqualTo(inv.valuation(plusDays(90)).gross());
    }

    @Test
    void rejectsAmountsBelowTheProductMinimumAndInactiveProducts() {
        assertThatThrownBy(() -> Investment.apply(CUSTOMER, ACCOUNT, TERM_90, Money.brl("99.99"), T0))
                .isInstanceOf(InvestmentBelowMinimumException.class);
        assertThat(Investment.apply(CUSTOMER, ACCOUNT, TERM_90, Money.brl("100.00"), T0).principal())
                .isEqualTo(Money.brl("100.00"));
        var off = new InvestmentProduct("X", "X", InvestmentKind.DAILY, new BigDecimal("0.1"), null, BigDecimal.ONE, false);
        assertThatThrownBy(() -> Investment.apply(CUSTOMER, ACCOUNT, off, Money.brl("10.00"), T0))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Investment.apply(CUSTOMER, ACCOUNT, DAILY, Money.brl("0.00"), T0))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void ownershipIsByCustomer() {
        var inv = daily("10.00");

        assertThat(inv.isOwnedBy(CUSTOMER)).isTrue();
        assertThat(inv.isOwnedBy(CustomerId.newId())).isFalse();
    }
}

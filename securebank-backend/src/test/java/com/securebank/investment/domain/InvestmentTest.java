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
    void aPartialRedeemPaysExactlyWhatWasAskedAndTheRestKeepsYielding() {
        var inv = daily("1000.00");
        Instant at = plusDays(100);
        Money before = inv.valuation(at).net();

        Money paid = inv.redeem(at, Money.brl("300.00"));

        assertThat(paid).isEqualTo(Money.brl("300.00"));
        assertThat(inv.status()).isEqualTo(InvestmentStatus.ACTIVE);
        assertThat(inv.principal().isLessThan(Money.brl("1000.00"))).isTrue();
        Money after = inv.valuation(at).net();
        assertThat(after.amount()).isBetween(before.minus(paid).amount().subtract(new BigDecimal("0.02")),
                before.minus(paid).amount().add(new BigDecimal("0.02"))); // sobra o que não foi resgatado (±1 centavo)
        assertThat(inv.valuation(plusDays(200)).gross().isGreaterThan(inv.valuation(at).gross())).isTrue(); // segue rendendo
    }

    @Test
    void askingForTheWholeNetOrMoreRedeemsEverything() {
        var inv = daily("1000.00");
        Money net = inv.valuation(plusDays(50)).net();

        Money paid = inv.redeem(plusDays(50), net.plus(Money.brl("500.00")));

        assertThat(paid).isEqualTo(net);
        assertThat(inv.status()).isEqualTo(InvestmentStatus.REDEEMED);
    }

    @Test
    void severalPartialRedeemsNeverPayMoreThanTheWholeWasWorth() {
        var inv = daily("1000.00");
        Money worth = inv.valuation(plusDays(30)).net();
        Money total = Money.zero(worth.currency());

        for (int i = 0; i < 3; i++) {
            total = total.plus(inv.redeem(plusDays(30), Money.brl("100.00")));
        }
        total = total.plus(inv.redeem(plusDays(30))); // o resto

        assertThat(total.amount()).isBetween(worth.amount().subtract(new BigDecimal("0.05")), worth.amount().add(new BigDecimal("0.05")));
    }

    @Test
    void aPartialRedeemRespectsTheSameRulesAsAFullOne() {
        var term = Investment.apply(CUSTOMER, ACCOUNT, TERM_90, Money.brl("1000.00"), T0);
        assertThatThrownBy(() -> term.redeem(plusDays(10), Money.brl("100.00")))
                .isInstanceOf(InvestmentNotMaturedException.class);
        assertThat(term.redeem(plusDays(90), Money.brl("100.00"))).isEqualTo(Money.brl("100.00"));

        var inv = daily("1000.00");
        assertThatThrownBy(() -> inv.redeem(plusDays(5), Money.brl("0.00"))).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> inv.redeem(plusDays(5), Money.brl("0.00").minus(Money.brl("1.00"))))
                .isInstanceOf(InvalidValueException.class);
        inv.redeem(plusDays(5));
        assertThatThrownBy(() -> inv.redeem(plusDays(6), Money.brl("1.00")))
                .isInstanceOf(InvestmentAlreadyRedeemedException.class);
    }

    @Test
    void whatRemainsAfterAPartialRedeemMatchesTheNetMinusWhatWasPaidToTheCent() {
        // antes o principal era cortado ao centavo e o que sobrava podia desviar até R$ 0,01: agora tem 8 casas
        for (int day : new int[] {0, 1, 17, 90, 100, 365, 400, 800}) {
            for (String wanted : new String[] {"0.01", "0.07", "1.00", "33.33", "123.45", "299.99", "500.00", "999.00"}) {
                var inv = daily("1000.00");
                Instant at = plusDays(day);
                Money before = inv.valuation(at).net();
                if (before.compareTo(Money.brl(wanted)) <= 0) {
                    continue;
                }

                inv.redeem(at, Money.brl(wanted));

                BigDecimal expected = before.amount().subtract(new BigDecimal(wanted));
                BigDecimal actual = inv.valuation(at).net().amount();
                assertThat(actual.subtract(expected).abs()).as("dia %d, resgate %s", day, wanted)
                        .isLessThanOrEqualTo(new BigDecimal("0.01")); // só o arredondamento do último centavo
            }
        }
    }

    @Test
    void manySmallPartialRedeemsAddUpToTheWholeValueWithoutDrift() {
        var inv = daily("1000.00");
        Instant at = plusDays(120);
        Money worth = inv.valuation(at).net();
        Money paid = Money.zero(worth.currency());

        for (int i = 0; i < 40; i++) {
            paid = paid.plus(inv.redeem(at, Money.brl("7.77")));
        }
        paid = paid.plus(inv.redeem(at)); // o que sobrou

        assertThat(inv.status()).isEqualTo(InvestmentStatus.REDEEMED);
        assertThat(paid.amount().subtract(worth.amount()).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
    }

    @Test
    void thePrincipalIsShownRoundedButKeptExact() {
        var inv = daily("1000.00");
        inv.redeem(plusDays(100), Money.brl("333.33"));

        assertThat(inv.principalExact().scale()).isEqualTo(8);
        assertThat(inv.principal().amount().scale()).isEqualTo(2);
        assertThat(inv.principal().amount()).isEqualByComparingTo(inv.principalExact().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void aSingleRemainingCentStaysApplied() {
        var inv = daily("1.00");

        Money paid = inv.redeem(plusDays(0), Money.brl("0.99"));

        assertThat(paid).isEqualTo(Money.brl("0.99"));
        assertThat(inv.status()).isEqualTo(InvestmentStatus.ACTIVE);
        assertThat(inv.principal()).isEqualTo(Money.brl("0.01"));
        assertThat(inv.valuation(plusDays(0)).net()).isEqualTo(Money.brl("0.01"));
    }

    @Test
    void ownershipIsByCustomer() {
        var inv = daily("10.00");

        assertThat(inv.isOwnedBy(CUSTOMER)).isTrue();
        assertThat(inv.isOwnedBy(CustomerId.newId())).isFalse();
    }
}

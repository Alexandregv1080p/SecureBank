package com.securebank.piggy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CurrencyMismatchException;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class PiggyTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-02T10:00:00Z");
    private static final Currency USD = Currency.getInstance("USD");

    private final CustomerId owner = CustomerId.newId();
    private final AccountId account = AccountId.newId();

    private Piggy piggy(String goal) {
        return Piggy.create(owner, account, "Viagem", goal == null ? null : Money.brl(goal), Money.BRL, T0);
    }

    // ---------- criação
    @Test
    void newPiggyIsActiveAndEmpty() {
        Piggy p = piggy("1000.00");

        assertThat(p.status()).isEqualTo(PiggyStatus.ACTIVE);
        assertThat(p.balance()).isEqualTo(Money.zero(Money.BRL));
        assertThat(p.goal()).isEqualTo(Money.brl("1000.00"));
        assertThat(p.name()).isEqualTo("Viagem");
        assertThat(p.accountId()).isEqualTo(account);
        assertThat(p.createdAt()).isEqualTo(T0);
        assertThat(p.updatedAt()).isEqualTo(T0);
        assertThat(p.isOwnedBy(owner)).isTrue();
        assertThat(p.isOwnedBy(CustomerId.newId())).isFalse();
    }

    @Test
    void goalIsOptional() {
        Piggy p = piggy(null);
        assertThat(p.goal()).isNull();
        assertThat(p.progressPercent()).isNull();
        assertThat(p.goalReached()).isFalse();
    }

    @Test
    void nameIsTrimmedRequiredAndLimitedTo40Characters() {
        assertThat(Piggy.create(owner, account, "  Casa  ", null, Money.BRL, T0).name()).isEqualTo("Casa");
        assertThat(Piggy.create(owner, account, "x".repeat(40), null, Money.BRL, T0).name()).hasSize(40);
        assertThatThrownBy(() -> Piggy.create(owner, account, "x".repeat(41), null, Money.BRL, T0))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Piggy.create(owner, account, "   ", null, Money.BRL, T0))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Piggy.create(owner, account, null, null, Money.BRL, T0))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void goalMustBePositiveAndInTheAccountCurrency() {
        assertThatThrownBy(() -> Piggy.create(owner, account, "A", Money.zero(Money.BRL), Money.BRL, T0))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Piggy.create(owner, account, "A", new Money(new BigDecimal("10"), USD), Money.BRL, T0))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void creationRequiresItsInputs() {
        assertThatThrownBy(() -> Piggy.create(null, account, "A", null, Money.BRL, T0)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Piggy.create(owner, null, "A", null, Money.BRL, T0)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Piggy.create(owner, account, "A", null, null, T0)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Piggy.create(owner, account, "A", null, Money.BRL, null)).isInstanceOf(InvalidValueException.class);
    }

    // ---------- guardar e resgatar
    @Test
    void depositAddsToTheBalance() {
        Piggy p = piggy(null);

        p.deposit(Money.brl("30.00"), T1);
        p.deposit(Money.brl("20.50"), T1);

        assertThat(p.balance()).isEqualTo(Money.brl("50.50"));
        assertThat(p.updatedAt()).isEqualTo(T1);
    }

    @Test
    void withdrawSubtractsAndNeverGoesNegative() {
        Piggy p = piggy(null);
        p.deposit(Money.brl("50.00"), T0);

        p.withdraw(Money.brl("20.00"), T1);
        assertThat(p.balance()).isEqualTo(Money.brl("30.00"));
        p.withdraw(Money.brl("30.00"), T1); // exatamente o saldo: pode
        assertThat(p.balance()).isEqualTo(Money.zero(Money.BRL));

        assertThatThrownBy(() -> p.withdraw(Money.brl("0.01"), T1)).isInstanceOf(InsufficientPiggyFundsException.class);
        assertThat(p.balance()).isEqualTo(Money.zero(Money.BRL));
        assertThat(p.updatedAt()).isEqualTo(T1);
    }

    @Test
    void amountsMustBePositiveAndInTheSameCurrency() {
        Piggy p = piggy(null);
        p.deposit(Money.brl("10.00"), T0);

        for (Money bad : new Money[] {Money.zero(Money.BRL), Money.brl("-1.00"), null}) {
            assertThatThrownBy(() -> p.deposit(bad, T1)).isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> p.withdraw(bad, T1)).isInstanceOf(InvalidValueException.class);
        }
        Money dollars = new Money(new BigDecimal("1.00"), USD);
        assertThatThrownBy(() -> p.deposit(dollars, T1)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> p.withdraw(dollars, T1)).isInstanceOf(CurrencyMismatchException.class);
        assertThat(p.balance()).isEqualTo(Money.brl("10.00")); // nada mudou
        assertThat(p.updatedAt()).isEqualTo(T0);
    }

    // ---------- meta
    @Test
    void goalReachedIsSignalledExactlyOnceByTheDepositThatCrossesIt() {
        Piggy p = piggy("100.00");

        assertThat(p.deposit(Money.brl("60.00"), T0)).isFalse();
        assertThat(p.goalReached()).isFalse();
        assertThat(p.deposit(Money.brl("40.00"), T0)).isTrue(); // 100,00 = meta: atingida
        assertThat(p.goalReached()).isTrue();
        assertThat(p.deposit(Money.brl("10.00"), T0)).isFalse(); // já tinha atingido: não avisa de novo
    }

    @Test
    void withdrawingBelowTheGoalAndCrossingAgainSignalsAgain() {
        Piggy p = piggy("100.00");
        p.deposit(Money.brl("100.00"), T0);
        p.withdraw(Money.brl("10.00"), T0);

        assertThat(p.goalReached()).isFalse();
        assertThat(p.deposit(Money.brl("10.00"), T0)).isTrue();
    }

    @Test
    void progressIsRoundedDownAndCappedAt100() {
        Piggy p = piggy("300.00");
        assertThat(p.progressPercent()).isZero();

        p.deposit(Money.brl("100.00"), T0);
        assertThat(p.progressPercent()).isEqualTo(33); // 33,33...: para baixo
        p.deposit(Money.brl("199.99"), T0);
        assertThat(p.progressPercent()).isEqualTo(99); // 299,99/300: ainda não é 100
        p.deposit(Money.brl("0.01"), T0);
        assertThat(p.progressPercent()).isEqualTo(100);
        p.deposit(Money.brl("500.00"), T0);
        assertThat(p.progressPercent()).isEqualTo(100); // passou da meta: continua 100
    }

    @Test
    void goalCanBeChangedAndCleared() {
        Piggy p = piggy("100.00");
        p.deposit(Money.brl("50.00"), T0);

        p.changeGoal(Money.brl("50.00"), T1);
        assertThat(p.goal()).isEqualTo(Money.brl("50.00"));
        assertThat(p.goalReached()).isTrue();
        assertThat(p.updatedAt()).isEqualTo(T1);

        p.clearGoal(T1);
        assertThat(p.goal()).isNull();
        assertThat(p.goalReached()).isFalse();

        assertThatThrownBy(() -> p.changeGoal(null, T1)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> p.changeGoal(Money.zero(Money.BRL), T1)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void renameValidatesLikeCreation() {
        Piggy p = piggy(null);

        p.rename("  Reserva  ", T1);

        assertThat(p.name()).isEqualTo("Reserva");
        assertThat(p.updatedAt()).isEqualTo(T1);
        assertThatThrownBy(() -> p.rename(" ", T1)).isInstanceOf(InvalidValueException.class);
        assertThat(p.name()).isEqualTo("Reserva");
    }

    // ---------- fechar
    @Test
    void onlyAnEmptyPiggyCanBeClosed() {
        Piggy p = piggy(null);
        p.deposit(Money.brl("5.00"), T0);

        assertThatThrownBy(() -> p.close(T1)).isInstanceOf(PiggyHasBalanceException.class);
        assertThat(p.status()).isEqualTo(PiggyStatus.ACTIVE);

        p.withdraw(Money.brl("5.00"), T1);
        p.close(T1);

        assertThat(p.status()).isEqualTo(PiggyStatus.CLOSED);
        assertThat(p.updatedAt()).isEqualTo(T1);
    }

    @Test
    void aClosedPiggyRefusesEverything() {
        Piggy p = piggy("10.00");
        p.close(T0);

        assertThatThrownBy(() -> p.deposit(Money.brl("1.00"), T1)).isInstanceOf(PiggyClosedException.class);
        assertThatThrownBy(() -> p.withdraw(Money.brl("1.00"), T1)).isInstanceOf(PiggyClosedException.class);
        assertThatThrownBy(() -> p.rename("Outro", T1)).isInstanceOf(PiggyClosedException.class);
        assertThatThrownBy(() -> p.changeGoal(Money.brl("5.00"), T1)).isInstanceOf(PiggyClosedException.class);
        assertThatThrownBy(() -> p.clearGoal(T1)).isInstanceOf(PiggyClosedException.class);
        assertThatThrownBy(() -> p.close(T1)).isInstanceOf(PiggyClosedException.class);
    }

    @Test
    void restoreKeepsEveryField() {
        Piggy original = piggy("100.00");
        original.deposit(Money.brl("10.00"), T1);

        Piggy copy = Piggy.restore(original.id(), owner, account, "Viagem", Money.brl("100.00"), Money.brl("10.00"),
                PiggyStatus.ACTIVE, T0, T1);

        assertThat(copy.id()).isEqualTo(original.id());
        assertThat(copy.balance()).isEqualTo(original.balance());
        assertThat(copy.customerId()).isEqualTo(owner);
        assertThat(copy.progressPercent()).isEqualTo(10);
    }
}

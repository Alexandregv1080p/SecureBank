package com.securebank.account.domain;

import static com.securebank.DomainFixtures.NOW;
import static com.securebank.DomainFixtures.USD;
import static com.securebank.DomainFixtures.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.CurrencyMismatchException;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionStatus;
import com.securebank.transaction.domain.TransactionType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountTest {

    @Test
    void opensActiveWithZeroBalance() {
        Account account = account("0.00");
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(Money.zero(Money.BRL));
    }

    @Test
    void depositCreditsBalanceAndReturnsTheLedgerEntry() {
        Account account = account("100.00");

        Transaction tx = account.deposit(Money.brl("50.25"), "ref-1", NOW);

        assertThat(account.balance()).isEqualTo(Money.brl("150.25"));
        assertThat(tx.accountId()).isEqualTo(account.id());
        assertThat(tx.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.direction()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(tx.amount()).isEqualTo(Money.brl("50.25"));
        assertThat(tx.balanceAfter()).isEqualTo(Money.brl("150.25"));
        assertThat(tx.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.reference()).isEqualTo("ref-1");
    }

    @Test
    void withdrawDebitsAndCanEmptyTheAccount() {
        Account account = account("100.00");

        Transaction tx = account.withdraw(Money.brl("100.00"), null, NOW);

        assertThat(account.balance().isZero()).isTrue();
        assertThat(tx.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(tx.balanceAfter().isZero()).isTrue();
    }

    @Test
    void balanceNeverGoesNegative() {
        Account account = account("100.00");

        assertThatThrownBy(() -> account.withdraw(Money.brl("100.01"), null, NOW))
                .isInstanceOf(InsufficientFundsException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(account.balance()).isEqualTo(Money.brl("100.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    void rejectsNonPositiveAmounts(String amount) {
        Account account = account("100.00");
        assertThatThrownBy(() -> account.deposit(Money.brl(amount), null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> account.withdraw(Money.brl(amount), null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThat(account.balance()).isEqualTo(Money.brl("100.00"));
    }

    @Test
    void rejectsForeignCurrencyWithoutTouchingTheBalance() {
        Account account = account("100.00");
        Money dollars = new Money(BigDecimal.TEN, USD);

        assertThatThrownBy(() -> account.deposit(dollars, null, NOW)).isInstanceOf(CurrencyMismatchException.class);
        assertThat(account.balance()).isEqualTo(Money.brl("100.00"));
    }

    @Test
    void blockedAccountCannotMoveMoneyInEitherDirection() {
        Account account = account("100.00");
        account.block(NOW);

        assertThatThrownBy(() -> account.deposit(Money.brl("1.00"), null, NOW))
                .isInstanceOf(AccountNotActiveException.class);
        assertThatThrownBy(() -> account.withdraw(Money.brl("1.00"), null, NOW))
                .isInstanceOf(AccountNotActiveException.class);

        account.unblock(NOW);
        assertThat(account.withdraw(Money.brl("1.00"), null, NOW)).isNotNull();
    }

    @Test
    void cannotCloseWithBalanceAndClosedIsTerminal() {
        Account account = account("10.00");
        assertThatThrownBy(() -> account.close(NOW)).isInstanceOf(AccountHasBalanceException.class);

        account.withdraw(Money.brl("10.00"), null, NOW);
        account.close(NOW);

        assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
        assertThatThrownBy(() -> account.unblock(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> account.deposit(Money.brl("1.00"), null, NOW))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void ownershipCheckBacksResourceAuthorization() {
        Account account = account("0.00");
        assertThat(account.isOwnedBy(account.customerId())).isTrue();
        assertThat(account.isOwnedBy(CustomerId.newId())).isFalse();
    }

    @Test
    void validatesNumberAndBranchFormat() {
        assertThatThrownBy(() -> new AccountNumber("1234567")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Branch("01")).isInstanceOf(InvalidValueException.class);
    }
}

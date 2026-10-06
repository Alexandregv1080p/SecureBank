package com.securebank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Guardar no porquinho é um débito da conta; resgatar é um crédito. Os dois ficam no extrato com tipo próprio. */
class AccountPiggyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    private Account fundedAccount(String balance) {
        Account account = Account.open(CustomerId.newId(), new AccountNumber("123456-7"), new Branch("0001"),
                AccountType.CHECKING, Money.BRL, NOW);
        if (!balance.equals("0.00")) {
            account.deposit(Money.brl(balance), null, NOW);
        }
        return account;
    }

    @Test
    void savingDebitsTheAccountAndRecordsAPiggyInEntry() {
        Account account = fundedAccount("100.00");

        Transaction tx = account.saveToPiggy(Money.brl("30.00"), "piggy:abc", NOW);

        assertThat(account.balance()).isEqualTo(Money.brl("70.00"));
        assertThat(tx.type()).isEqualTo(TransactionType.PIGGY_IN);
        assertThat(tx.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(tx.amount()).isEqualTo(Money.brl("30.00"));
        assertThat(tx.balanceAfter()).isEqualTo(Money.brl("70.00"));
        assertThat(tx.reference()).isEqualTo("piggy:abc");
    }

    @Test
    void redeemingCreditsTheAccountAndRecordsAPiggyOutEntry() {
        Account account = fundedAccount("100.00");

        Transaction tx = account.redeemFromPiggy(Money.brl("25.00"), "piggy:abc", NOW);

        assertThat(account.balance()).isEqualTo(Money.brl("125.00"));
        assertThat(tx.type()).isEqualTo(TransactionType.PIGGY_OUT);
        assertThat(tx.direction()).isEqualTo(TransactionDirection.CREDIT);
    }

    @Test
    void savingMoreThanTheBalanceIsRefused() {
        Account account = fundedAccount("10.00");

        assertThatThrownBy(() -> account.saveToPiggy(Money.brl("10.01"), "piggy:abc", NOW))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.balance()).isEqualTo(Money.brl("10.00"));
    }

    @Test
    void aBlockedAccountCannotMoveMoneyToOrFromAPiggy() {
        Account account = fundedAccount("10.00");
        account.block(NOW);

        assertThatThrownBy(() -> account.saveToPiggy(Money.brl("1.00"), "piggy:abc", NOW))
                .isInstanceOf(AccountNotActiveException.class);
        assertThatThrownBy(() -> account.redeemFromPiggy(Money.brl("1.00"), "piggy:abc", NOW))
                .isInstanceOf(AccountNotActiveException.class);
    }
}

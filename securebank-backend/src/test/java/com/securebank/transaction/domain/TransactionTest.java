package com.securebank.transaction.domain;

import static com.securebank.DomainFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static Transaction completed() {
        return Transaction.completed(AccountId.newId(), TransactionType.DEPOSIT, TransactionDirection.CREDIT,
                Money.brl("10.00"), Money.brl("10.00"), "ref", NOW);
    }

    @Test
    void completedCanBeReversedButNeverResurrected() {
        Transaction tx = completed();
        tx.reverse();

        assertThat(tx.status()).isEqualTo(TransactionStatus.REVERSED);
        assertThatThrownBy(tx::complete).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(tx::reverse).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void completedCannotBecomeFailed() {
        assertThatThrownBy(() -> completed().fail()).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void asyncLifecycleGoesThroughProcessing() {
        TransactionStatus pending = TransactionStatus.PENDING;
        assertThat(pending.canTransitionTo(TransactionStatus.PROCESSING)).isTrue();
        assertThat(pending.canTransitionTo(TransactionStatus.COMPLETED)).isFalse();
        assertThat(TransactionStatus.PROCESSING.canTransitionTo(TransactionStatus.COMPLETED)).isTrue();
        assertThat(TransactionStatus.FAILED.canTransitionTo(TransactionStatus.COMPLETED)).isFalse();
    }

    @Test
    void requiresPositiveAmountAndNonBlankReference() {
        assertThatThrownBy(() -> Transaction.completed(AccountId.newId(), TransactionType.DEPOSIT,
                TransactionDirection.CREDIT, Money.zero(Money.BRL), Money.zero(Money.BRL), null, NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Transaction.completed(AccountId.newId(), TransactionType.DEPOSIT,
                TransactionDirection.CREDIT, Money.brl("1.00"), Money.brl("1.00"), " ", NOW))
                .isInstanceOf(InvalidValueException.class);
    }
}

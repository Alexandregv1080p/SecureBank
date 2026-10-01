package com.securebank.payment.domain;

import static com.securebank.DomainFixtures.NOW;
import static com.securebank.DomainFixtures.account;
import static com.securebank.DomainFixtures.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.account.domain.Account;
import com.securebank.account.domain.InsufficientFundsException;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitExceededException;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionType;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private static final String BARCODE = "34191790010104351004791020150008291070026000"; // 44 dígitos
    private static final Money NOTHING_USED = Money.zero(Money.BRL);

    private final PaymentService service = new PaymentService();
    private final Account account = account("500.00");
    private final Limit limit = Limit.defaultFor(account.id(), LimitType.PAYMENT, NOW); // 10.000 / 20.000

    private Payment payment(String amount) {
        return Payment.create(account.id(), Money.brl(amount), BARCODE, "conta de luz", key(), NOW);
    }

    @Test
    void debitsTheAccountAndCompletesThePayment() {
        Payment payment = payment("120.50");

        Transaction tx = service.execute(payment, account, limit, NOTHING_USED, NOW);

        assertThat(account.balance()).isEqualTo(Money.brl("379.50"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.transactionId()).isEqualTo(tx.id());
        assertThat(tx.type()).isEqualTo(TransactionType.PAYMENT);
        assertThat(tx.direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(tx.reference()).isEqualTo(payment.id().toString());
    }

    @Test
    void insufficientFundsLeavesAccountAndPaymentUntouched() {
        Payment payment = payment("500.01");

        assertThatThrownBy(() -> service.execute(payment, account, limit, NOTHING_USED, NOW))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.balance()).isEqualTo(Money.brl("500.00"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void limitExceededLeavesAccountAndPaymentUntouched() {
        Payment payment = payment("100.00");

        assertThatThrownBy(() -> service.execute(payment, account, limit, Money.brl("19950.00"), NOW))
                .isInstanceOf(LimitExceededException.class);

        assertThat(account.balance()).isEqualTo(Money.brl("500.00"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void aProcessedPaymentIsNeverChargedTwice() {
        Payment payment = payment("100.00");
        service.execute(payment, account, limit, NOTHING_USED, NOW);

        assertThatThrownBy(() -> service.execute(payment, account, limit, NOTHING_USED, NOW))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThat(account.balance()).isEqualTo(Money.brl("400.00"));
    }

    @Test
    void rejectsAccountOrLimitFromAnotherOwner() {
        Payment payment = payment("10.00");
        Account other = account("500.00");

        assertThatThrownBy(() -> service.execute(payment, other, limit, NOTHING_USED, NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void createValidatesBarcodeAmountAndDescription() {
        assertThatThrownBy(() -> Payment.create(account.id(), Money.brl("1.00"), "123", null, key(), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Payment.create(account.id(), Money.zero(Money.BRL), BARCODE, null, key(), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Payment.create(account.id(), Money.brl("1.00"), BARCODE, "x".repeat(141), key(), NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void failIsAllowedFromPendingButNotAfterCompletion() {
        Payment failed = payment("10.00");
        failed.fail("LIMIT_EXCEEDED", NOW);
        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);

        Payment done = payment("10.00");
        service.execute(done, account, limit, NOTHING_USED, NOW);
        assertThatThrownBy(() -> done.fail("late", NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }
}

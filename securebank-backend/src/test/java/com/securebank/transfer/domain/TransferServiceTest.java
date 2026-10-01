package com.securebank.transfer.domain;

import static com.securebank.DomainFixtures.NOW;
import static com.securebank.DomainFixtures.USD;
import static com.securebank.DomainFixtures.account;
import static com.securebank.DomainFixtures.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNotActiveException;
import com.securebank.account.domain.InsufficientFundsException;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitExceededException;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CurrencyMismatchException;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionType;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private static final Money NOTHING_USED = Money.zero(Money.BRL);

    private final TransferService service = new TransferService();
    private final Account source = account("1000.00");
    private final Account destination = account("50.00");
    private final Limit limit = Limit.defaultFor(source.id(), LimitType.TRANSFER, NOW); // 5.000 / 10.000

    private Transfer transfer(String amount) {
        return Transfer.request(source.id(), destination.id(), Money.brl(amount), "aluguel", key(), NOW);
    }

    @Test
    void movesTheMoneyAndLinksBothLedgerEntries() {
        Transfer transfer = transfer("300.00");

        TransferService.Result result = service.execute(transfer, source, destination, limit, NOTHING_USED, NOW);

        assertThat(source.balance()).isEqualTo(Money.brl("700.00"));
        assertThat(destination.balance()).isEqualTo(Money.brl("350.00"));
        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.debitTransactionId()).isEqualTo(result.debit().id());
        assertThat(transfer.creditTransactionId()).isEqualTo(result.credit().id());
        assertThat(result.debit().direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(result.credit().direction()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(result.debit().type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.debit().reference()).isEqualTo(transfer.id().toString()).isEqualTo(result.credit().reference());
    }

    @Test
    void moneyIsConserved() {
        Money before = source.balance().plus(destination.balance());

        service.execute(transfer("123.45"), source, destination, limit, NOTHING_USED, NOW);

        assertThat(source.balance().plus(destination.balance())).isEqualTo(before);
    }

    @Test
    void insufficientFundsChangesNothing() {
        Transfer transfer = transfer("1000.01");

        assertThatThrownBy(() -> service.execute(transfer, source, destination, limit, NOTHING_USED, NOW))
                .isInstanceOf(InsufficientFundsException.class);

        assertUntouched(transfer, "1000.00", "50.00");
    }

    @Test
    void blockedDestinationIsDetectedBeforeTheSourceIsDebited() {
        destination.block(NOW);
        Transfer transfer = transfer("100.00");

        assertThatThrownBy(() -> service.execute(transfer, source, destination, limit, NOTHING_USED, NOW))
                .isInstanceOf(AccountNotActiveException.class);

        assertUntouched(transfer, "1000.00", "50.00");
    }

    @Test
    void blockedSourceCannotSend() {
        source.block(NOW);
        Transfer transfer = transfer("100.00");

        assertThatThrownBy(() -> service.execute(transfer, source, destination, limit, NOTHING_USED, NOW))
                .isInstanceOf(AccountNotActiveException.class);

        assertUntouched(transfer, "1000.00", "50.00");
    }

    @Test
    void differentCurrenciesAreRejectedBeforeAnyDebit() {
        Account dollarAccount = account(USD, "0.00");
        Transfer transfer = Transfer.request(source.id(), dollarAccount.id(), Money.brl("10.00"), null, key(), NOW);

        assertThatThrownBy(() -> service.execute(transfer, source, dollarAccount, limit, NOTHING_USED, NOW))
                .isInstanceOf(CurrencyMismatchException.class);

        assertThat(source.balance()).isEqualTo(Money.brl("1000.00"));
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    void perOperationAndDailyLimitsStopTheTransferWithoutMovingMoney() {
        Account rich = account("100000.00");
        Limit richLimit = Limit.defaultFor(rich.id(), LimitType.TRANSFER, NOW);
        Transfer big = Transfer.request(rich.id(), destination.id(), Money.brl("5000.01"), null, key(), NOW);
        Transfer small = Transfer.request(rich.id(), destination.id(), Money.brl("1500.00"), null, key(), NOW);

        assertThatThrownBy(() -> service.execute(big, rich, destination, richLimit, NOTHING_USED, NOW))
                .isInstanceOfSatisfying(LimitExceededException.class,
                        e -> assertThat(e.scope()).isEqualTo(LimitExceededException.Scope.PER_OPERATION));
        assertThatThrownBy(() -> service.execute(small, rich, destination, richLimit, Money.brl("9000.00"), NOW))
                .isInstanceOfSatisfying(LimitExceededException.class,
                        e -> assertThat(e.scope()).isEqualTo(LimitExceededException.Scope.DAILY));

        assertThat(rich.balance()).isEqualTo(Money.brl("100000.00"));
        assertThat(destination.balance()).isEqualTo(Money.brl("50.00"));
    }

    @Test
    void aCompletedTransferCannotBeExecutedTwice() {
        Transfer transfer = transfer("100.00");
        service.execute(transfer, source, destination, limit, NOTHING_USED, NOW);

        assertThatThrownBy(() -> service.execute(transfer, source, destination, limit, NOTHING_USED, NOW))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThat(source.balance()).isEqualTo(Money.brl("900.00")); // debitou uma vez só
    }

    @Test
    void rejectsAccountsThatAreNotTheOnesInTheTransfer() {
        Account stranger = account("1000.00");
        Transfer transfer = transfer("10.00");

        assertThatThrownBy(() -> service.execute(transfer, stranger, destination, limit, NOTHING_USED, NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> service.execute(transfer, source, destination,
                Limit.defaultFor(source.id(), LimitType.PAYMENT, NOW), NOTHING_USED, NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void requestValidatesItsInvariants() {
        assertThatThrownBy(() -> Transfer.request(source.id(), source.id(), Money.brl("1.00"), null, key(), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Transfer.request(source.id(), AccountId.newId(), Money.zero(Money.BRL), null, key(), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Transfer.request(source.id(), AccountId.newId(), Money.brl("1.00"), "x".repeat(141),
                key(), NOW)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void failIsRecordedOnlyFromPending() {
        Transfer transfer = transfer("10.00");
        transfer.fail("INSUFFICIENT_FUNDS", NOW);

        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.failureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThatThrownBy(() -> transfer.complete(null, null, NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    private void assertUntouched(Transfer transfer, String sourceBalance, String destinationBalance) {
        assertThat(source.balance()).isEqualTo(Money.brl(sourceBalance));
        assertThat(destination.balance()).isEqualTo(Money.brl(destinationBalance));
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.debitTransactionId()).isNull();
    }
}

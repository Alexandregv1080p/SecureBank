package com.securebank.pix.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.AccountType;
import com.securebank.account.domain.Branch;
import com.securebank.account.domain.InsufficientFundsException;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PixRefundTest {

    private static final Instant SENT_AT = Instant.parse("2026-07-01T12:00:00Z");
    private static final String E2E = "E00000000202607011200abcdefghijk";
    private static final String E2E_REFUND = "E00000000202607021200zyxwvutsrqp";

    private final PixService service = new PixService();
    private final Account payer = account("100001-0", "100.00");    // enviou o Pix original
    private final Account receiver = account("100002-0", "0.00");   // recebeu e vai devolver
    private final PixTransfer original = settledPix("60.00");

    private Account account(String number, String balance) {
        Account a = Account.open(CustomerId.newId(), new AccountNumber(number), new Branch("0001"), AccountType.CHECKING,
                Money.BRL, SENT_AT);
        if (!balance.equals("0.00")) {
            a.deposit(Money.brl(balance), null, SENT_AT);
        }
        return a;
    }

    /** Executa um Pix de verdade entre as duas contas, deixando o saldo coerente (payer 40,00 / receiver 60,00). */
    private PixTransfer settledPix(String amount) {
        PixTransfer pix = PixTransfer.create(payer.id(), receiver.id(), Money.brl(amount), "almoço", "bob@example.com",
                "Ana S***", "Bob L***", E2E, SENT_AT);
        service.execute(pix, payer, receiver, Limit.defaultFor(payer.id(), LimitType.PIX, SENT_AT), Money.zero(Money.BRL), SENT_AT);
        return pix;
    }

    private PixTransfer refundOf(PixTransfer of, String amount, Instant now) {
        return PixTransfer.refundOf(of, Money.brl(amount), E2E_REFUND, now);
    }

    private PixService.Result refund(PixTransfer refund, String alreadyRefunded, Instant now) {
        return service.refund(original, refund, receiver, payer, Money.brl(alreadyRefunded), now);
    }

    @Test
    void aRefundGoesBackwardsBetweenTheSameAccounts() {
        PixTransfer refund = refundOf(original, "25.00", SENT_AT.plus(Duration.ofDays(1)));

        assertThat(refund.sourceAccountId()).isEqualTo(receiver.id());
        assertThat(refund.destinationAccountId()).isEqualTo(payer.id());
        assertThat(refund.refundOfId()).isEqualTo(original.id());
        assertThat(refund.isRefund()).isTrue();
        assertThat(refund.message()).isEqualTo("Devolução");
        assertThat(refund.sourceName()).isEqualTo("Bob L***");       // os nomes também trocam de lado
        assertThat(refund.destinationName()).isEqualTo("Ana S***");
        assertThat(original.isRefund()).isFalse();
    }

    @Test
    void refundingMovesTheMoneyBackWithItsOwnTransactionTypes() {
        PixTransfer refund = refundOf(original, "25.00", SENT_AT.plus(Duration.ofDays(1)));

        PixService.Result result = refund(refund, "0.00", SENT_AT.plus(Duration.ofDays(1)));

        assertThat(receiver.balance()).isEqualTo(Money.brl("35.00"));
        assertThat(payer.balance()).isEqualTo(Money.brl("65.00"));
        assertThat(result.debit().type()).isEqualTo(TransactionType.PIX_RETURN_OUT);
        assertThat(result.debit().direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(result.credit().type()).isEqualTo(TransactionType.PIX_RETURN_IN);
        assertThat(result.credit().direction()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(refund.debitTransactionId()).isEqualTo(result.debit().id());
    }

    @Test
    void theWholeAmountCanBeRefundedButNotACentMore() {
        Instant now = SENT_AT.plus(Duration.ofDays(2));

        assertThatThrownBy(() -> refund(refundOf(original, "60.01", now), "0.00", now)).isInstanceOf(PixRefundException.class)
                .extracting("code").isEqualTo("PIX_REFUND_EXCEEDS");
        refund(refundOf(original, "60.00", now), "0.00", now);

        assertThat(receiver.balance()).isEqualTo(Money.zero(Money.BRL));
        assertThat(payer.balance()).isEqualTo(Money.brl("100.00"));
    }

    @Test
    void partialRefundsAddUpToTheOriginalAmount() {
        Instant now = SENT_AT.plus(Duration.ofDays(2));

        refund(refundOf(original, "20.00", now), "0.00", now);
        refund(refundOf(original, "30.00", now), "20.00", now);

        assertThatThrownBy(() -> refund(refundOf(original, "10.01", now), "50.00", now)).isInstanceOf(PixRefundException.class);
        refund(refundOf(original, "10.00", now), "50.00", now); // exatamente o que resta
        assertThat(original.refundable(Money.brl("60.00"), now)).isEqualTo(Money.zero(Money.BRL));
    }

    @Test
    void refundableShrinksWithWhatWasAlreadyReturned() {
        assertThat(original.refundable(Money.zero(Money.BRL), SENT_AT)).isEqualTo(Money.brl("60.00"));
        assertThat(original.refundable(Money.brl("25.50"), SENT_AT)).isEqualTo(Money.brl("34.50"));
        assertThat(original.refundable(Money.brl("60.00"), SENT_AT)).isEqualTo(Money.zero(Money.BRL));
        assertThat(original.refundable(Money.brl("70.00"), SENT_AT)).isEqualTo(Money.zero(Money.BRL)); // nunca negativo
    }

    @Test
    void theWindowIs90DaysAndTheLastInstantStillCounts() {
        Instant lastMoment = SENT_AT.plus(Duration.ofDays(90));
        Instant tooLate = lastMoment.plusSeconds(1);

        assertThat(original.refundable(Money.zero(Money.BRL), lastMoment)).isEqualTo(Money.brl("60.00"));
        assertThat(original.refundable(Money.zero(Money.BRL), tooLate)).isEqualTo(Money.zero(Money.BRL));
        refund(refundOf(original, "10.00", lastMoment), "0.00", lastMoment);
        assertThatThrownBy(() -> refund(refundOf(original, "10.00", tooLate), "10.00", tooLate)).isInstanceOf(PixRefundException.class)
                .extracting("code").isEqualTo("PIX_REFUND_EXPIRED");
    }

    @Test
    void aRefundCannotBeRefundedAgain() {
        Instant now = SENT_AT.plus(Duration.ofDays(1));
        PixTransfer first = refundOf(original, "10.00", now);
        refund(first, "0.00", now);
        PixTransfer refundOfRefund = PixTransfer.refundOf(first, Money.brl("5.00"), E2E_REFUND, now);

        assertThat(first.refundable(Money.zero(Money.BRL), now)).isEqualTo(Money.zero(Money.BRL));
        assertThatThrownBy(() -> service.refund(first, refundOfRefund, payer, receiver, Money.zero(Money.BRL), now))
                .isInstanceOf(PixRefundException.class).extracting("code").isEqualTo("PIX_NOT_REFUNDABLE");
    }

    @Test
    void aRefusedRefundLeavesBothAccountsUntouched() {
        Instant now = SENT_AT.plus(Duration.ofDays(1));
        receiver.withdraw(Money.brl("55.00"), null, SENT_AT); // o recebedor já gastou quase tudo (sobram 5,00)

        assertThatThrownBy(() -> refund(refundOf(original, "10.00", now), "0.00", now)).isInstanceOf(InsufficientFundsException.class);

        assertThat(receiver.balance()).isEqualTo(Money.brl("5.00"));
        assertThat(payer.balance()).isEqualTo(Money.brl("40.00"));
    }

    @Test
    void theRefundIsRefusedWhenTheAccountsOrTheLinkDoNotMatch() {
        Instant now = SENT_AT.plus(Duration.ofDays(1));
        PixTransfer refund = refundOf(original, "10.00", now);
        Account stranger = account("100003-0", "100.00");
        PixTransfer otherOriginal = settledPix("5.00");

        assertThatThrownBy(() -> service.refund(original, refund, stranger, payer, Money.zero(Money.BRL), now)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> service.refund(original, refund, receiver, stranger, Money.zero(Money.BRL), now)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> service.refund(otherOriginal, refund, receiver, payer, Money.zero(Money.BRL), now)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void refundInputsAreValidated() {
        Instant now = SENT_AT;

        assertThatThrownBy(() -> PixTransfer.refundOf(null, Money.brl("1.00"), E2E_REFUND, now)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.refundOf(original, null, E2E_REFUND, now)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.refundOf(original, Money.brl("1.00"), null, now)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.refundOf(original, Money.brl("1.00"), E2E_REFUND, null)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.refundOf(original, Money.zero(Money.BRL), E2E_REFUND, now)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void aRefundDoesNotConsumeThePixLimit() {
        // a devolução usa tipos de lançamento próprios: o limite diário do Pix (que soma só PIX_OUT) não é afetado
        assertThat(LimitType.PIX.transactionType()).isEqualTo(TransactionType.PIX_OUT);
        assertThat(LimitType.PIX.transactionType()).isNotEqualTo(TransactionType.PIX_RETURN_OUT);
    }
}

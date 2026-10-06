package com.securebank.pix.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.AccountType;
import com.securebank.account.domain.Branch;
import com.securebank.account.domain.InsufficientFundsException;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitExceededException;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PixDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-02T14:30:00Z");
    private static final String E2E = "E00000000202610021430abcdefghijk";

    private final CustomerId alice = CustomerId.newId();
    private final CustomerId bob = CustomerId.newId();

    private Account account(CustomerId owner, String number, String balance) {
        Account account = Account.open(owner, new AccountNumber(number), new Branch("0001"), AccountType.CHECKING,
                Money.BRL, NOW);
        if (!balance.equals("0.00")) {
            account.deposit(Money.brl(balance), null, NOW);
        }
        return account;
    }

    private PixTransfer pix(Account from, Account to, String amount, String message) {
        return PixTransfer.create(from.id(), to.id(), Money.brl(amount), message, "bob@example.com", "Ana S***",
                "Bob L***", E2E, NOW);
    }

    // ---------- chave
    @Test
    void registeringAKeyNormalizesItsValue() {
        PixKey key = PixKey.register(alice, AccountId.newId(), PixKeyType.EMAIL, " Ana@Example.com ", NOW);

        assertThat(key.value()).isEqualTo("ana@example.com");
        assertThat(key.type()).isEqualTo(PixKeyType.EMAIL);
        assertThat(key.createdAt()).isEqualTo(NOW);
        assertThat(key.isOwnedBy(alice)).isTrue();
        assertThat(key.isOwnedBy(bob)).isFalse();
    }

    @Test
    void aKeyRequiresItsInputsAndAValidValue() {
        AccountId account = AccountId.newId();
        assertThatThrownBy(() -> PixKey.register(null, account, PixKeyType.CPF, "52998224725", NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKey.register(alice, null, PixKeyType.CPF, "52998224725", NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKey.register(alice, account, null, "52998224725", NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKey.register(alice, account, PixKeyType.CPF, null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKey.register(alice, account, PixKeyType.CPF, "52998224725", null)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKey.register(alice, account, PixKeyType.CPF, "123", NOW)).isInstanceOf(InvalidValueException.class);
    }

    // ---------- máscaras e identificador
    @Test
    void namesAreMaskedKeepingTheFirstNameAndTheInitials() {
        assertThat(PixMasks.name("Ana Souza Lima")).isEqualTo("Ana S*** L***");
        assertThat(PixMasks.name("  Ana   Souza ")).isEqualTo("Ana S***");
        assertThat(PixMasks.name("Madonna")).isEqualTo("Madonna");
        assertThat(PixMasks.name(" ")).isEmpty();
        assertThat(PixMasks.name(null)).isEmpty();
    }

    @Test
    void endToEndIdHasTheCentralBankFormat() {
        String id = EndToEndId.generate(NOW, new Random(7));

        assertThat(id).hasSize(32).startsWith("E00000000202610021430").matches("E\\d{20}[A-Za-z0-9]{11}");
        assertThat(EndToEndId.generate(NOW, new Random(8))).isNotEqualTo(id);
    }

    // ---------- Pix
    @Test
    void aPixNeedsItsInputsAPositiveAmountAndDifferentAccounts() {
        Account a = account(alice, "100001-0", "100.00");
        Account b = account(bob, "100002-0", "0.00");

        assertThatThrownBy(() -> PixTransfer.create(null, b.id(), Money.brl("1.00"), null, "k", "A", "B", E2E, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(a.id(), null, Money.brl("1.00"), null, "k", "A", "B", E2E, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(a.id(), b.id(), null, null, "k", "A", "B", E2E, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(a.id(), b.id(), Money.brl("1.00"), null, null, "A", "B", E2E, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(a.id(), b.id(), Money.brl("1.00"), null, "k", "A", "B", null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(a.id(), b.id(), Money.brl("1.00"), null, "k", "A", "B", E2E, null)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(a.id(), a.id(), Money.brl("1.00"), null, "k", "A", "B", E2E, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixTransfer.create(a.id(), b.id(), Money.zero(Money.BRL), null, "k", "A", "B", E2E, NOW)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void theMessageIsTrimmedOptionalAndLimitedTo140() {
        Account a = account(alice, "100001-0", "100.00");
        Account b = account(bob, "100002-0", "0.00");

        assertThat(pix(a, b, "1.00", "  oi  ").message()).isEqualTo("oi");
        assertThat(pix(a, b, "1.00", "   ").message()).isNull();
        assertThat(pix(a, b, "1.00", null).message()).isNull();
        assertThat(pix(a, b, "1.00", "x".repeat(140)).message()).hasSize(140);
        assertThatThrownBy(() -> pix(a, b, "1.00", "x".repeat(141))).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void executingMovesTheMoneyAndLinksBothEntries() {
        Account a = account(alice, "100001-0", "100.00");
        Account b = account(bob, "100002-0", "5.00");
        PixTransfer pix = pix(a, b, "30.00", "almoço");
        Limit limit = Limit.defaultFor(a.id(), LimitType.PIX, NOW);

        PixService.Result result = new PixService().execute(pix, a, b, limit, Money.zero(Money.BRL), NOW);

        assertThat(a.balance()).isEqualTo(Money.brl("70.00"));
        assertThat(b.balance()).isEqualTo(Money.brl("35.00"));
        assertThat(result.debit().type()).isEqualTo(TransactionType.PIX_OUT);
        assertThat(result.debit().direction()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(result.credit().type()).isEqualTo(TransactionType.PIX_IN);
        assertThat(result.credit().direction()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(result.debit().reference()).isEqualTo(E2E);
        assertThat(pix.debitTransactionId()).isEqualTo(result.debit().id());
        assertThat(pix.creditTransactionId()).isEqualTo(result.credit().id());
    }

    @Test
    void aRefusedPixLeavesBothAccountsUntouched() {
        Account a = account(alice, "100001-0", "10.00");
        Account b = account(bob, "100002-0", "0.00");
        Limit limit = Limit.defaultFor(a.id(), LimitType.PIX, NOW);

        assertThatThrownBy(() -> new PixService().execute(pix(a, b, "10.01", null), a, b, limit, Money.zero(Money.BRL), NOW))
                .isInstanceOf(InsufficientFundsException.class);
        b.block(NOW);
        assertThatThrownBy(() -> new PixService().execute(pix(a, b, "5.00", null), a, b, limit, Money.zero(Money.BRL), NOW))
                .isNotNull(); // destino bloqueado não recebe

        assertThat(a.balance()).isEqualTo(Money.brl("10.00"));
        assertThat(b.balance()).isEqualTo(Money.zero(Money.BRL));
    }

    @Test
    void thePixLimitAppliesPerOperationAndPerDay() {
        Account a = account(alice, "100001-0", "100000.00");
        Account b = account(bob, "100002-0", "0.00");
        Limit limit = Limit.defaultFor(a.id(), LimitType.PIX, NOW); // 5.000 por operação, 10.000 por dia

        assertThatThrownBy(() -> new PixService().execute(pix(a, b, "5000.01", null), a, b, limit, Money.zero(Money.BRL), NOW))
                .isInstanceOf(LimitExceededException.class);
        assertThatThrownBy(() -> new PixService().execute(pix(a, b, "2000.00", null), a, b, limit, Money.brl("8000.01"), NOW))
                .isInstanceOf(LimitExceededException.class);
        new PixService().execute(pix(a, b, "2000.00", null), a, b, limit, Money.brl("8000.00"), NOW); // exatamente o teto: passa

        assertThat(b.balance()).isEqualTo(Money.brl("2000.00"));
    }

    @Test
    void theServiceRefusesMismatchedAccountsOrLimitType() {
        Account a = account(alice, "100001-0", "100.00");
        Account b = account(bob, "100002-0", "0.00");
        Account c = account(bob, "100003-0", "0.00");
        PixTransfer pix = pix(a, b, "1.00", null);

        assertThatThrownBy(() -> new PixService().execute(pix, a, c, Limit.defaultFor(a.id(), LimitType.PIX, NOW), Money.zero(Money.BRL), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PixService().execute(pix, c, b, Limit.defaultFor(c.id(), LimitType.PIX, NOW), Money.zero(Money.BRL), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PixService().execute(pix, a, b, Limit.defaultFor(a.id(), LimitType.TRANSFER, NOW), Money.zero(Money.BRL), NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PixService().execute(pix, a, b, Limit.defaultFor(b.id(), LimitType.PIX, NOW), Money.zero(Money.BRL), NOW))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void aPixCanOnlyBeSettledOnce() {
        Account a = account(alice, "100001-0", "100.00");
        Account b = account(bob, "100002-0", "0.00");
        PixTransfer pix = pix(a, b, "1.00", null);
        pix.settle(TransactionId.newId(), TransactionId.newId());

        assertThatThrownBy(() -> pix.settle(TransactionId.newId(), TransactionId.newId())).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void accountPixMethodsRecordTheirOwnTransactionTypes() {
        Account a = account(alice, "100001-0", "50.00");

        assertThat(a.pixOut(Money.brl("10.00"), E2E, NOW).type()).isEqualTo(TransactionType.PIX_OUT);
        assertThat(a.pixIn(Money.brl("5.00"), E2E, NOW).type()).isEqualTo(TransactionType.PIX_IN);
        assertThat(a.balance()).isEqualTo(Money.brl("45.00"));
    }
}

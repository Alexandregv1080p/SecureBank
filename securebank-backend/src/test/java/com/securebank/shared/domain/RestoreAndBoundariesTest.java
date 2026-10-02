package com.securebank.shared.domain;

import static com.securebank.DomainFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.DomainFixtures;
import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.AccountStatus;
import com.securebank.account.domain.AccountType;
import com.securebank.account.domain.Branch;
import com.securebank.authentication.domain.MfaDevice;
import com.securebank.authentication.domain.MfaStatus;
import com.securebank.authentication.domain.PasswordPolicy;
import com.securebank.authentication.domain.RefreshToken;
import com.securebank.authentication.domain.Session;
import com.securebank.authentication.domain.SessionRevocation;
import com.securebank.authentication.domain.User;
import com.securebank.authentication.domain.UserStatus;
import com.securebank.authorization.domain.Role;
import com.securebank.customer.domain.Customer;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.outbox.domain.EventTopics;
import com.securebank.payment.domain.Payment;
import com.securebank.payment.domain.PaymentStatus;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionStatus;
import com.securebank.transaction.domain.TransactionType;
import com.securebank.transfer.domain.Transfer;
import com.securebank.transfer.domain.TransferStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reconstituir um agregado a partir do banco não pode perder nem trocar nenhum campo, e as fronteiras das regras
 * (tamanhos, limites) precisam estar fixadas por teste: é onde os testes de mutação mais pegam.
 */
class RestoreAndBoundariesTest {

    private static final Instant LATER = NOW.plusSeconds(120);
    private static final Email EMAIL = new Email("ana.souza@example.com");

    // ---------- restore preserva todos os campos ----------

    @Test
    void customerAndAccountSurviveARoundTrip() {
        Customer customer = DomainFixtures.customer();
        customer.block(LATER);
        Customer restoredCustomer = Customer.restore(customer.id(), customer.name(), customer.document(), customer.email(),
                customer.phone(), customer.status(), customer.createdAt(), customer.updatedAt());
        assertThat(restoredCustomer).usingRecursiveComparison().isEqualTo(customer);

        Account account = DomainFixtures.account("100.00");
        account.block(LATER);
        Account restored = Account.restore(account.id(), account.customerId(), account.accountNumber(), account.branch(),
                account.type(), account.status(), account.balance(), account.createdAt(), account.updatedAt());
        assertThat(restored).usingRecursiveComparison().isEqualTo(account);
        assertThat(restored.status()).isEqualTo(AccountStatus.BLOCKED);
    }

    @Test
    void transactionRoundTripsAndFollowsItsLifecycle() {
        Account account = DomainFixtures.account("0.00");
        Transaction done = account.deposit(Money.brl("10.00"), "ref", NOW);
        Transaction restored = Transaction.restore(done.id(), done.accountId(), done.type(), done.direction(), done.amount(),
                done.balanceAfter(), done.status(), done.reference(), done.createdAt());
        assertThat(restored).usingRecursiveComparison().isEqualTo(done);

        Transaction pending = Transaction.restore(TransactionId.newId(), account.id(), TransactionType.PAYMENT,
                TransactionDirection.DEBIT, Money.brl("5.00"), Money.brl("5.00"), TransactionStatus.PENDING, null, NOW);
        pending.markProcessing();
        assertThat(pending.status()).isEqualTo(TransactionStatus.PROCESSING);
        pending.complete();
        pending.reverse();
        assertThat(pending.status()).isEqualTo(TransactionStatus.REVERSED);
        assertThatThrownBy(pending::markProcessing).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void transferAndPaymentRoundTripWithTheirLinks() {
        Transfer transfer = Transfer.request(AccountId.newId(), AccountId.newId(), Money.brl("9.99"), "aluguel",
                DomainFixtures.key(), NOW);
        transfer.complete(TransactionId.newId(), TransactionId.newId(), LATER);
        Transfer restoredTransfer = Transfer.restore(transfer.id(), transfer.sourceAccountId(), transfer.destinationAccountId(),
                transfer.amount(), transfer.description(), transfer.idempotencyKey(), transfer.status(),
                transfer.debitTransactionId(), transfer.creditTransactionId(), transfer.failureReason(), transfer.createdAt(),
                transfer.updatedAt());
        assertThat(restoredTransfer).usingRecursiveComparison().isEqualTo(transfer);
        assertThat(restoredTransfer.status()).isEqualTo(TransferStatus.COMPLETED);

        Payment payment = Payment.create(AccountId.newId(), Money.brl("1.00"), "34191790010104351004791020150008291070026000",
                "conta", DomainFixtures.key(), NOW);
        payment.fail("LIMIT_EXCEEDED", LATER);
        Payment restoredPayment = Payment.restore(payment.id(), payment.accountId(), payment.amount(), payment.barcode(),
                payment.description(), payment.idempotencyKey(), payment.status(), payment.transactionId(),
                payment.failureReason(), payment.createdAt(), payment.updatedAt());
        assertThat(restoredPayment).usingRecursiveComparison().isEqualTo(payment);
        assertThat(restoredPayment.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void limitRoundTripsAndChangeValidatesCurrencyAndBounds() {
        Limit limit = Limit.defaultFor(AccountId.newId(), LimitType.PAYMENT, NOW);
        Limit restored = Limit.restore(limit.accountId(), limit.type(), limit.perOperation(), limit.daily(), limit.updatedAt());
        assertThat(restored).usingRecursiveComparison().isEqualTo(limit);

        limit.change(Money.brl("300.00"), Money.brl("300.00"), LATER); // por operação = diário é válido
        assertThat(limit.updatedAt()).isEqualTo(LATER);
        Money dollars = new Money(java.math.BigDecimal.TEN, Currency.getInstance("USD"));
        assertThatThrownBy(() -> limit.change(dollars, dollars, LATER)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> limit.change(Money.brl("10.00"), dollars, LATER)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> limit.change(null, Money.brl("10.00"), LATER)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> limit.change(Money.brl("10.00"), null, LATER)).isInstanceOf(InvalidValueException.class);
        assertThat(limit.perOperation()).isEqualTo(Money.brl("300.00")); // nada rejeitado foi aplicado
    }

    @Test
    void authenticationAggregatesSurviveARoundTrip() {
        User user = User.staff(EMAIL, "hash", Role.ADMIN, NOW);
        user.disable(LATER);
        User restoredUser = User.restore(user.id(), user.email(), user.passwordHash(), user.role(), user.customerId(),
                user.status(), user.createdAt(), user.updatedAt());
        assertThat(restoredUser).usingRecursiveComparison().isEqualTo(user);
        assertThat(restoredUser.status()).isEqualTo(UserStatus.DISABLED);
        user.changePassword("novo-hash", LATER.plusSeconds(1));
        assertThat(user.passwordHash()).isEqualTo("novo-hash");
        assertThatThrownBy(() -> user.changePassword(" ", LATER)).isInstanceOf(InvalidValueException.class);

        Session session = Session.start(UserId.newId(), true, "10.0.0.0", "ua", Duration.ofDays(1), NOW);
        session.touch(LATER);
        session.revoke(SessionRevocation.PASSWORD_CHANGED, LATER.plusSeconds(5));
        Session restoredSession = Session.restore(session.id(), session.userId(), session.mfaVerified(), session.createdAt(),
                session.lastUsedAt(), session.expiresAt(), session.revokedAt(), session.revocation(), session.ip(),
                session.userAgent());
        assertThat(restoredSession).usingRecursiveComparison().isEqualTo(session);
        assertThat(session.lastUsedAt()).isEqualTo(LATER);

        RefreshToken token = RefreshToken.issue(SessionId.newId(), NOW, NOW.plusSeconds(60)).record();
        RefreshToken restoredToken = RefreshToken.restore(token.id(), token.sessionId(), token.tokenHash(), token.createdAt(),
                token.expiresAt(), LATER);
        assertThat(restoredToken.wasUsed()).isTrue();
        assertThat(restoredToken.createdAt()).isEqualTo(token.createdAt());
        assertThat(token.isExpired(NOW.plusSeconds(59))).isFalse(); // a fronteira exata: expira em t >= expiresAt
        assertThat(token.isExpired(NOW.plusSeconds(60))).isTrue();
        assertThatThrownBy(() -> RefreshToken.issue(SessionId.newId(), NOW, NOW)).isInstanceOf(InvalidValueException.class);

        MfaDevice device = MfaDevice.pending(UserId.newId(), "cipher", NOW);
        MfaDevice restoredDevice = MfaDevice.restore(device.id(), device.userId(), device.secretCipher(), MfaStatus.ACTIVE, 42,
                device.createdAt(), LATER);
        assertThat(restoredDevice.isActive()).isTrue();
        assertThat(restoredDevice.lastUsedStep()).isEqualTo(42);
        assertThat(restoredDevice.confirmedAt()).isEqualTo(LATER);
    }

    @Test
    void accountNumberAndBranchKeepTheirValues() {
        assertThat(new AccountNumber("123456-7").toString()).isEqualTo("123456-7");
        assertThat(new Branch("0001").toString()).isEqualTo("0001");
        assertThat(Account.open(CustomerId.newId(), new AccountNumber("123456-7"), new Branch("0001"), AccountType.SAVINGS,
                Money.BRL, NOW).type()).isEqualTo(AccountType.SAVINGS);
    }

    // ---------- tópicos ----------

    @Test
    void everyEventTypeMapsToItsTopic() {
        assertThat(EventTopics.forEvent("TransferCompleted")).isEqualTo(EventTopics.TRANSFERS);
        assertThat(EventTopics.forEvent("TransferFailed")).isEqualTo(EventTopics.TRANSFERS);
        assertThat(EventTopics.forEvent("PaymentCompleted")).isEqualTo(EventTopics.PAYMENTS);
        assertThat(EventTopics.forEvent("AccountBlocked")).isEqualTo(EventTopics.ACCOUNTS);
        assertThat(EventTopics.forEvent("AccountUnblocked")).isEqualTo(EventTopics.ACCOUNTS);
        assertThat(EventTopics.forEvent("UserLoggedIn")).isEqualTo(EventTopics.USERS);
        assertThatThrownBy(() -> EventTopics.forEvent("Whatever")).isInstanceOf(IllegalArgumentException.class);
        assertThat(EventTopics.ALL).hasSize(4).doesNotHaveDuplicates();
    }

    // ---------- fronteiras da política de senha ----------

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef-12345", "abcdeabcdeabcde"}) // 12 caracteres; exatamente 5 distintos
    void acceptsTheLowerBoundaries(String password) {
        PasswordPolicy.validate(password, EMAIL);
    }

    @Test
    void acceptsExactly128AndRejects129CharactersAndFourDistinct() {
        String max = "aB3-x".repeat(25) + "9Zq"; // 128
        PasswordPolicy.validate(max, EMAIL);
        assertThatThrownBy(() -> PasswordPolicy.validate(max + "x", EMAIL)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdabcdabcd", EMAIL)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("Abcdef-1234", EMAIL)).isInstanceOf(InvalidValueException.class); // 11
    }

    @Test
    void theEmailRuleStartsAtFourCharactersOfTheLocalPart() {
        PasswordPolicy.validate("xxabcxx-9876-QW", new Email("abc@example.com")); // parte local de 3: permitido
        assertThatThrownBy(() -> PasswordPolicy.validate("xxabcdxx-9876-QW", new Email("abcd@example.com")))
                .isInstanceOf(InvalidValueException.class); // parte local de 4: proibido
        assertThatThrownBy(() -> PasswordPolicy.validate("XXABCDXX-9876-qw", new Email("abcd@example.com")))
                .isInstanceOf(InvalidValueException.class); // sem diferenciar maiúsculas
    }

    @Test
    void passwordPolicyRejectsNull() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null, EMAIL)).isInstanceOf(InvalidValueException.class);
        assertThat(UUID.randomUUID()).isNotNull(); // (mantém o import usado em outros cenários)
    }
}

package com.securebank.authentication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.authorization.domain.Permission;
import com.securebank.authorization.domain.Role;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthenticationDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Email EMAIL = new Email("ana.souza@example.com");

    // ---------- política de senha ----------

    @Test
    void acceptsALongPassphrase() {
        PasswordPolicy.validate("Correct-Horse-Battery-9", EMAIL);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "short", "elevenchar1", "password1234", "111111111111", "aaaaabbbbbaa",
            "xx-ana.souza-xx-12345"})
    void rejectsShortCommonRepetitiveOrEmailBasedPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password, EMAIL)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rejectsPasswordsAboveTheMaximumToLimitHashingCost() {
        assertThatThrownBy(() -> PasswordPolicy.validate("aB3-".repeat(40), EMAIL))
                .isInstanceOf(InvalidValueException.class);
    }

    // ---------- papéis (menor privilégio) ----------

    @Test
    void rolesGetOnlyWhatTheyNeed() {
        assertThat(Role.CUSTOMER.permissions()).contains(Permission.CREATE_TRANSFER, Permission.VIEW_STATEMENT)
                .doesNotContain(Permission.MANAGE_LIMITS, Permission.VIEW_AUDIT, Permission.MANAGE_USERS,
                        Permission.VIEW_CUSTOMER);
        assertThat(Role.SUPPORT.permissions()).containsExactlyInAnyOrder(Permission.VIEW_CUSTOMER, Permission.VIEW_AUDIT);
        assertThat(Role.ADMIN.permissions()).contains(Permission.MANAGE_USERS, Permission.MANAGE_LIMITS,
                Permission.MANAGE_ACCOUNTS, Permission.VIEW_AUDIT);
        // equipe não opera contas de clientes: nada de permissões de cliente
        for (Role staff : new Role[] {Role.SUPPORT, Role.ADMIN}) {
            assertThat(staff.permissions()).doesNotContain(Permission.VIEW_ACCOUNT, Permission.CREATE_TRANSFER,
                    Permission.DEPOSIT, Permission.WITHDRAW, Permission.CREATE_PAYMENT);
            assertThat(staff.isStaff()).isTrue();
        }
        assertThat(Role.CUSTOMER.isStaff()).isFalse();
    }

    // ---------- usuário ----------

    @Test
    void customerUsersNeedACustomerAndStaffMustNotHaveOne() {
        User customer = User.forCustomer(EMAIL, "hash", CustomerId.newId(), NOW);
        assertThat(customer.role()).isEqualTo(Role.CUSTOMER);
        assertThat(customer.customerId()).isNotNull();

        User staff = User.staff(EMAIL, "hash", Role.SUPPORT, NOW);
        assertThat(staff.customerId()).isNull();

        assertThatThrownBy(() -> User.forCustomer(EMAIL, "hash", null, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> User.staff(EMAIL, "hash", Role.CUSTOMER, NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> User.staff(EMAIL, " ", Role.ADMIN, NOW)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void disableAndEnableAreExplicitTransitions() {
        User user = User.staff(EMAIL, "hash", Role.SUPPORT, NOW);

        user.disable(NOW);
        assertThat(user.isActive()).isFalse();
        assertThatThrownBy(() -> user.disable(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        user.enable(NOW);
        assertThat(user.isActive()).isTrue();
        assertThatThrownBy(() -> user.enable(NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    // ---------- sessão ----------

    @Test
    void sessionExpiresAtItsAbsoluteLimitAndRevocationIsIdempotent() {
        Session session = Session.start(UserId.newId(), false, "10.0.0.0", "agent", Duration.ofDays(30), NOW);

        assertThat(session.isActive(NOW.plus(Duration.ofDays(29)))).isTrue();
        assertThat(session.isActive(NOW.plus(Duration.ofDays(30)))).isFalse(); // vida máxima, mesmo sem revogação

        session.revoke(SessionRevocation.LOGOUT, NOW.plusSeconds(10));
        session.revoke(SessionRevocation.REUSE_DETECTED, NOW.plusSeconds(99)); // mantém o 1º motivo
        assertThat(session.revocation()).isEqualTo(SessionRevocation.LOGOUT);
        assertThat(session.revokedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(session.isActive(NOW.plusSeconds(11))).isFalse();
    }

    @Test
    void sessionTruncatesAHugeUserAgent() {
        Session session = Session.start(UserId.newId(), true, null, "x".repeat(5000), Duration.ofDays(1), NOW);
        assertThat(session.userAgent()).hasSize(200);
    }

    // ---------- refresh token ----------

    @Test
    void refreshTokenKeepsOnlyTheHashAndHasEnoughEntropy() {
        RefreshToken.Issued first = RefreshToken.issue(SessionId.newId(), NOW, NOW.plus(Duration.ofDays(7)));
        RefreshToken.Issued second = RefreshToken.issue(SessionId.newId(), NOW, NOW.plus(Duration.ofDays(7)));

        assertThat(first.rawValue()).hasSize(43).isNotEqualTo(second.rawValue()); // 256 bits em base64url
        assertThat(first.record().tokenHash()).isEqualTo(RefreshToken.hash(first.rawValue())).isNotEqualTo(first.rawValue());
        assertThat(first.record().isExpired(NOW.plus(Duration.ofDays(7)))).isTrue();
        assertThat(first.record().wasUsed()).isFalse();
    }

    // ---------- MFA ----------

    @Test
    void mfaDeviceActivatesOnceAndRejectsStepReuse() {
        MfaDevice device = MfaDevice.pending(UserId.newId(), "cipher", NOW);
        assertThat(device.isActive()).isFalse();

        device.activate(100, NOW);
        assertThat(device.isActive()).isTrue();
        assertThat(device.lastUsedStep()).isEqualTo(100);
        assertThatThrownBy(() -> device.activate(101, NOW)).isInstanceOf(InvalidStateTransitionException.class);

        device.recordUse(101);
        assertThatThrownBy(() -> device.recordUse(101)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> device.recordUse(50)).isInstanceOf(InvalidValueException.class);
    }
}

package com.securebank.authentication.domain;

import com.securebank.authorization.domain.Role;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.UserId;
import java.time.Instant;

/**
 * Credencial de acesso. Cliente (papel CUSTOMER) é ligado a um Customer; equipe (SUPPORT/ADMIN) não tem Customer.
 * Guarda apenas o hash da senha, nunca a senha.
 */
public final class User {

    private final UserId id;
    private final Email email;
    private String passwordHash;
    private final Role role;
    private final CustomerId customerId;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Email email, String passwordHash, Role role, CustomerId customerId, UserStatus status,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.customerId = customerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User forCustomer(Email email, String passwordHash, CustomerId customerId, Instant now) {
        requireCommon(email, passwordHash, now);
        if (customerId == null) {
            throw new InvalidValueException("A customer user needs a customer");
        }
        return new User(UserId.newId(), email, passwordHash, Role.CUSTOMER, customerId, UserStatus.ACTIVE, now, now);
    }

    public static User staff(Email email, String passwordHash, Role role, Instant now) {
        requireCommon(email, passwordHash, now);
        if (role == null || !role.isStaff()) {
            throw new InvalidValueException("Staff role must be SUPPORT or ADMIN");
        }
        return new User(UserId.newId(), email, passwordHash, role, null, UserStatus.ACTIVE, now, now);
    }

    public static User restore(UserId id, Email email, String passwordHash, Role role, CustomerId customerId,
            UserStatus status, Instant createdAt, Instant updatedAt) {
        return new User(id, email, passwordHash, role, customerId, status, createdAt, updatedAt);
    }

    public void changePassword(String newPasswordHash, Instant now) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new InvalidValueException("Password hash is required");
        }
        this.passwordHash = newPasswordHash;
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        if (status == UserStatus.DISABLED) {
            throw new InvalidStateTransitionException("User", status, UserStatus.DISABLED);
        }
        this.status = UserStatus.DISABLED;
        this.updatedAt = now;
    }

    public void enable(Instant now) {
        if (status == UserStatus.ACTIVE) {
            throw new InvalidStateTransitionException("User", status, UserStatus.ACTIVE);
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    private static void requireCommon(Email email, String passwordHash, Instant now) {
        if (email == null || passwordHash == null || passwordHash.isBlank() || now == null) {
            throw new InvalidValueException("User requires email, password hash and time");
        }
    }

    public UserId id() { return id; }
    public Email email() { return email; }
    public String passwordHash() { return passwordHash; }
    public Role role() { return role; }
    public CustomerId customerId() { return customerId; }
    public UserStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

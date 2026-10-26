package com.securebank.authentication.domain;

import com.securebank.authorization.domain.Department;
import com.securebank.authorization.domain.Role;
import com.securebank.authorization.domain.StaffLevel;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.UserId;
import java.time.Instant;

/**
 * Credencial de acesso. Cliente (papel CUSTOMER) é ligado a um Customer; equipe tem departamento e nível, e não tem Customer.
 * Guarda apenas o hash da senha, nunca a senha.
 */
public final class User {

    private final UserId id;
    private final Email email;
    private String passwordHash;
    private Role role;
    private Department department;
    private StaffLevel level;
    private final CustomerId customerId;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Email email, String passwordHash, Role role, Department department, StaffLevel level,
            CustomerId customerId, UserStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.department = department;
        this.level = level;
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
        return new User(UserId.newId(), email, passwordHash, Role.CUSTOMER, null, null, customerId, UserStatus.ACTIVE, now, now);
    }

    public static User staff(Email email, String passwordHash, Department department, StaffLevel level, Instant now) {
        requireCommon(email, passwordHash, now);
        requirePosition(department, level);
        return new User(UserId.newId(), email, passwordHash, department.role(), department, level, null,
                UserStatus.ACTIVE, now, now);
    }

    public static User restore(UserId id, Email email, String passwordHash, Role role, Department department,
            StaffLevel level, CustomerId customerId, UserStatus status, Instant createdAt, Instant updatedAt) {
        return new User(id, email, passwordHash, role, department, level, customerId, status, createdAt, updatedAt);
    }

    /** Muda a pessoa de departamento e/ou de nível; o papel (e as permissões) acompanham o departamento. */
    public void assign(Department newDepartment, StaffLevel newLevel, Instant now) {
        if (customerId != null) {
            throw new InvalidValueException("Only staff users have a department");
        }
        requirePosition(newDepartment, newLevel);
        this.department = newDepartment;
        this.level = newLevel;
        this.role = newDepartment.role();
        this.updatedAt = now;
    }

    /** A Diretoria só tem gerentes: o papel ADMIN nunca é de analista. */
    private static void requirePosition(Department department, StaffLevel level) {
        if (department == null || level == null) {
            throw new InvalidValueException("Staff needs a department and a level");
        }
        if (department == Department.DIRETORIA && level != StaffLevel.MANAGER) {
            throw new InvalidValueException("Everyone in the board is a manager");
        }
    }

    public boolean isManager() {
        return level == StaffLevel.MANAGER;
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
    public Department department() { return department; }
    public StaffLevel level() { return level; }
    public CustomerId customerId() { return customerId; }
    public UserStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

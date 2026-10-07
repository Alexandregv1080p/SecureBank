package com.securebank.authorization.domain;

import static com.securebank.authorization.domain.Permission.*;

import java.util.EnumSet;
import java.util.Set;

/** Menor privilégio: cada papel só recebe o que precisa (seção 19). */
public enum Role {
    CUSTOMER(EnumSet.of(MANAGE_PROFILE, VIEW_ACCOUNT, VIEW_STATEMENT, OPEN_ACCOUNT, DEPOSIT, WITHDRAW,
            CREATE_TRANSFER, VIEW_TRANSFER, CREATE_PAYMENT, VIEW_PAYMENT, VIEW_NOTIFICATIONS,
            VIEW_PIGGY, MANAGE_PIGGY, VIEW_INVESTMENTS, MANAGE_INVESTMENTS, VIEW_PIX, MANAGE_PIX, SEND_PIX)),
    SUPPORT(EnumSet.of(VIEW_CUSTOMER, VIEW_AUDIT)),
    ADMIN(EnumSet.of(VIEW_CUSTOMER, VIEW_AUDIT, MANAGE_USERS, MANAGE_LIMITS, MANAGE_ACCOUNTS));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** Equipe do banco: não tem cliente associado nem acesso a contas de clientes como dono. */
    public boolean isStaff() {
        return this != CUSTOMER;
    }
}

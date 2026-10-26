package com.securebank.authorization.domain;

import static com.securebank.authorization.domain.Permission.*;

import java.util.EnumSet;
import java.util.Set;

/** Menor privilégio: cada papel só recebe o que precisa (seção 19). */
public enum Role {
    CUSTOMER(EnumSet.of(MANAGE_PROFILE, VIEW_ACCOUNT, VIEW_STATEMENT, OPEN_ACCOUNT, DEPOSIT, WITHDRAW,
            CREATE_TRANSFER, VIEW_TRANSFER, CREATE_PAYMENT, VIEW_PAYMENT, VIEW_NOTIFICATIONS,
            VIEW_PIGGY, MANAGE_PIGGY, VIEW_INVESTMENTS, MANAGE_INVESTMENTS, VIEW_FX, TRADE_FX, VIEW_PIX, MANAGE_PIX, SEND_PIX, VIEW_CARD, MANAGE_CARD)),
    /** Atendimento: consulta clientes e a auditoria, sem alterar nada. */
    SUPPORT(EnumSet.of(VIEW_CUSTOMER, VIEW_AUDIT)),
    /** Risco e fraude: bloqueia e desbloqueia contas e cartões. */
    RISK(EnumSet.of(VIEW_CUSTOMER, VIEW_AUDIT, MANAGE_ACCOUNTS, DECIDE_APPROVALS)),
    /** Crédito: decide limites de cartão e de operação. */
    CREDIT(EnumSet.of(VIEW_CUSTOMER, MANAGE_LIMITS, DECIDE_APPROVALS)),
    /** Financeiro: cotação e spread do câmbio e o painel gerencial. */
    FINANCE(EnumSet.of(VIEW_REPORTS, MANAGE_FX_RATES)),
    /** Auditor: só lê (clientes, auditoria e painel), nunca altera. */
    AUDITOR(EnumSet.of(VIEW_CUSTOMER, VIEW_AUDIT, VIEW_REPORTS)),
    ADMIN(EnumSet.of(VIEW_CUSTOMER, VIEW_AUDIT, MANAGE_USERS, MANAGE_LIMITS, MANAGE_ACCOUNTS, MANAGE_FX_RATES, VIEW_REPORTS, DECIDE_APPROVALS));

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

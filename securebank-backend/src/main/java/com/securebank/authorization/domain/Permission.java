package com.securebank.authorization.domain;

/** Ações que um papel pode executar. O token carrega só o papel; as permissões são resolvidas no servidor. */
public enum Permission {
    // cliente
    MANAGE_PROFILE, VIEW_ACCOUNT, VIEW_STATEMENT, OPEN_ACCOUNT, DEPOSIT, WITHDRAW,
    CREATE_TRANSFER, VIEW_TRANSFER, CREATE_PAYMENT, VIEW_PAYMENT, VIEW_NOTIFICATIONS,
    VIEW_PIGGY, MANAGE_PIGGY, VIEW_PIX, MANAGE_PIX, SEND_PIX,
    // equipe
    VIEW_CUSTOMER, VIEW_AUDIT, MANAGE_USERS, MANAGE_LIMITS, MANAGE_ACCOUNTS
}

package com.securebank.audit.application;

import com.securebank.audit.domain.AuditEvent;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.TransactionId;
import com.securebank.shared.domain.UserId;

/**
 * O que o chamador sabe sobre o evento. Quem fez (usuário, IP, traceId) vem do contexto da requisição;
 * {@code userId} só é informado quando o ator ainda não está autenticado (login) ou é outra pessoa.
 */
public record AuditEntry(AuditEvent event, UserId userId, AccountId accountId, TransactionId transactionId,
        String detail) {

    public static AuditEntry of(AuditEvent event) {
        return new AuditEntry(event, null, null, null, null);
    }

    public AuditEntry user(UserId userId) {
        return new AuditEntry(event, userId, accountId, transactionId, detail);
    }

    public AuditEntry account(AccountId accountId) {
        return new AuditEntry(event, userId, accountId, transactionId, detail);
    }

    public AuditEntry transaction(TransactionId transactionId) {
        return new AuditEntry(event, userId, accountId, transactionId, detail);
    }

    public AuditEntry detail(String detail) {
        return new AuditEntry(event, userId, accountId, transactionId, detail);
    }
}

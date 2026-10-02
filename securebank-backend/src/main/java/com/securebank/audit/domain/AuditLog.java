package com.securebank.audit.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.TransactionId;
import com.securebank.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/** Registro imutável de um evento. Nunca guarda senha, token, OTP nem IP completo. */
public record AuditLog(UUID id, Instant occurredAt, AuditEvent event, UserId userId, AccountId accountId,
        TransactionId transactionId, String ip, String traceId, String detail) {

    public static final int MAX_DETAIL = 500;

    public AuditLog {
        if (detail != null && detail.length() > MAX_DETAIL) {
            detail = detail.substring(0, MAX_DETAIL);
        }
    }
}

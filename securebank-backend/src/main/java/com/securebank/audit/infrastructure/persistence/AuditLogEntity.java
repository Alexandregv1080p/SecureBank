package com.securebank.audit.infrastructure.persistence;

import com.securebank.audit.domain.AuditEvent;
import com.securebank.audit.domain.AuditLog;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.TransactionId;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
class AuditLogEntity {

    @Id UUID id;
    Instant occurredAt;
    @Enumerated(EnumType.STRING) AuditEvent event;
    UUID userId;
    UUID accountId;
    UUID transactionId;
    String ip;
    String traceId;
    String detail;

    static AuditLogEntity of(AuditLog log) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.id = log.id();
        entity.occurredAt = log.occurredAt();
        entity.event = log.event();
        entity.userId = log.userId() == null ? null : log.userId().value();
        entity.accountId = log.accountId() == null ? null : log.accountId().value();
        entity.transactionId = log.transactionId() == null ? null : log.transactionId().value();
        entity.ip = log.ip();
        entity.traceId = log.traceId();
        entity.detail = log.detail();
        return entity;
    }

    AuditLog toDomain() {
        return new AuditLog(id, occurredAt, event, userId == null ? null : new UserId(userId),
                accountId == null ? null : new AccountId(accountId),
                transactionId == null ? null : new TransactionId(transactionId), ip, traceId, detail);
    }
}

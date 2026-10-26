package com.securebank.approval.infrastructure.persistence;

import com.securebank.approval.domain.ApprovalKind;
import com.securebank.approval.domain.ApprovalRequest;
import com.securebank.approval.domain.ApprovalStatus;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
class ApprovalEntity {

    @Id UUID id;
    @Enumerated(EnumType.STRING) ApprovalKind kind;
    UUID subjectId;
    BigDecimal amount;
    String note;
    UUID requestedBy;
    Instant createdAt;
    @Enumerated(EnumType.STRING) ApprovalStatus status;
    UUID decidedBy;
    String decisionNote;
    Instant decidedAt;
    @Version Long version;

    void apply(ApprovalRequest r) {
        id = r.id();
        kind = r.kind();
        subjectId = r.subjectId();
        amount = r.amount();
        note = r.note();
        requestedBy = r.requestedBy().value();
        createdAt = r.createdAt();
        status = r.status();
        decidedBy = r.decidedBy() == null ? null : r.decidedBy().value();
        decisionNote = r.decisionNote();
        decidedAt = r.decidedAt();
    }

    ApprovalRequest toDomain() {
        return ApprovalRequest.restore(id, kind, subjectId, amount, note, new UserId(requestedBy), createdAt, status,
                decidedBy == null ? null : new UserId(decidedBy), decisionNote, decidedAt);
    }
}

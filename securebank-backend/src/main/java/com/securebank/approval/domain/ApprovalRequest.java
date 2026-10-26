package com.securebank.approval.domain;

import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido que passou da alçada de quem o fez e espera a decisão de um gerente. Só sai de PENDING uma vez, e a
 * decisão guarda quem decidiu e o porquê. Quem pediu nunca decide o próprio pedido (regra do caso de uso).
 */
public final class ApprovalRequest {

    public static final int NOTE_MAX = 140;

    private final UUID id;
    private final ApprovalKind kind;
    private final UUID subjectId;
    private final BigDecimal amount;
    private final String note;
    private final UserId requestedBy;
    private final Instant createdAt;
    private ApprovalStatus status;
    private UserId decidedBy;
    private String decisionNote;
    private Instant decidedAt;

    private ApprovalRequest(UUID id, ApprovalKind kind, UUID subjectId, BigDecimal amount, String note, UserId requestedBy,
            Instant createdAt, ApprovalStatus status, UserId decidedBy, String decisionNote, Instant decidedAt) {
        this.id = id;
        this.kind = kind;
        this.subjectId = subjectId;
        this.amount = amount;
        this.note = note;
        this.requestedBy = requestedBy;
        this.createdAt = createdAt;
        this.status = status;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.decidedAt = decidedAt;
    }

    public static ApprovalRequest open(ApprovalKind kind, UUID subjectId, BigDecimal amount, String note, UserId requestedBy,
            Instant now) {
        if (kind == null || subjectId == null || requestedBy == null || now == null) {
            throw new InvalidValueException("Approval needs a kind, a subject, who asked and when");
        }
        if ((kind == ApprovalKind.CREDIT_LIMIT) != (amount != null)) {
            throw new InvalidValueException("Only a credit limit request carries an amount");
        }
        return new ApprovalRequest(UUID.randomUUID(), kind, subjectId, amount, optionalNote(note), requestedBy, now,
                ApprovalStatus.PENDING, null, null, null);
    }

    public static ApprovalRequest restore(UUID id, ApprovalKind kind, UUID subjectId, BigDecimal amount, String note,
            UserId requestedBy, Instant createdAt, ApprovalStatus status, UserId decidedBy, String decisionNote, Instant decidedAt) {
        return new ApprovalRequest(id, kind, subjectId, amount, note, requestedBy, createdAt, status, decidedBy, decisionNote, decidedAt);
    }

    public void approve(UserId by, String why, Instant now) {
        decide(ApprovalStatus.APPROVED, by, optionalNote(why), now);
    }

    /** Recusar exige dizer o motivo: quem pediu precisa saber o que mudar. */
    public void reject(UserId by, String why, Instant now) {
        String reason = optionalNote(why);
        if (reason == null || reason.length() < 3) {
            throw new InvalidValueException("A rejection needs a reason of 3 to " + NOTE_MAX + " characters");
        }
        decide(ApprovalStatus.REJECTED, by, reason, now);
    }

    public void cancel(UserId by, Instant now) {
        decide(ApprovalStatus.CANCELED, by, null, now);
    }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    private void decide(ApprovalStatus next, UserId by, String why, Instant now) {
        if (status != ApprovalStatus.PENDING) {
            throw new InvalidStateTransitionException("Approval", status, next);
        }
        if (by == null || now == null) {
            throw new InvalidValueException("A decision needs who decided and when");
        }
        this.status = next;
        this.decidedBy = by;
        this.decisionNote = why;
        this.decidedAt = now;
    }

    private static String optionalNote(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String clean = text.trim();
        if (clean.length() > NOTE_MAX || clean.contains("\n") || clean.contains("\r")) {
            throw new InvalidValueException("The note must have at most " + NOTE_MAX + " characters, in one line");
        }
        return clean;
    }

    public UUID id() { return id; }
    public ApprovalKind kind() { return kind; }
    public UUID subjectId() { return subjectId; }
    public BigDecimal amount() { return amount; }
    public String note() { return note; }
    public UserId requestedBy() { return requestedBy; }
    public Instant createdAt() { return createdAt; }
    public ApprovalStatus status() { return status; }
    public UserId decidedBy() { return decidedBy; }
    public String decisionNote() { return decisionNote; }
    public Instant decidedAt() { return decidedAt; }
}

package com.securebank.approval.infrastructure.web;

import com.securebank.approval.application.ApprovalService;
import com.securebank.approval.domain.ApprovalRequest;
import com.securebank.approval.domain.ApprovalStatus;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ações da equipe com alçada: quem pode age na hora (200); quem não pode abre um pedido para um gerente (202, com o
 * pedido no corpo). Também a fila de pedidos e a decisão.
 */
@RestController
@RequestMapping("/api/v1/admin")
class ApprovalController {

    record LimitRequest(@NotNull @DecimalMin("100.00") @Digits(integer = 9, fraction = 2) BigDecimal limit,
            @Size(max = 140) String note) {}

    record LimitApplied(UUID cardId, MoneyResponse creditLimit) {}

    record AccountUnblocked(UUID id, String status) {}

    record UnblockRequest(@Size(max = 140) String note) {}

    record DecisionRequest(@Size(max = 140) String note) {}

    record ApprovalView(UUID id, String kind, String status, UUID subjectId, String subject, UUID customerId, String amount,
            String note, String department, String requestedBy, Instant createdAt, String decidedBy, String decisionNote,
            Instant decidedAt, boolean canDecide, boolean canCancel) {

        static ApprovalView of(ApprovalService.Row row) {
            ApprovalRequest r = row.request();
            return new ApprovalView(r.id(), r.kind().name(), r.status().name(), r.subjectId(), row.subject(), row.customerId(),
                    r.amount() == null ? null : r.amount().toPlainString(), r.note(), r.kind().department().name(),
                    row.requesterEmail(), r.createdAt(), row.deciderEmail(), r.decisionNote(), r.decidedAt(), row.canDecide(),
                    row.canCancel());
        }
    }

    record PendingCount(long pending) {}

    private final ApprovalService approvals;

    ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @PutMapping("/credit/cards/{id}/limit")
    ResponseEntity<Object> changeLimit(@PathVariable String id, @Valid @RequestBody LimitRequest request) {
        CardId card = CardId.of(id);
        var outcome = approvals.changeCreditLimit(card, request.limit(), request.note());
        if (outcome.pending() != null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApprovalView.of(outcome.pending()));
        }
        return ResponseEntity.ok(new LimitApplied(card.value(), MoneyResponse.of(outcome.applied())));
    }

    @PostMapping("/accounts/{id}/unblock")
    ResponseEntity<Object> unblock(@PathVariable String id, @Valid @RequestBody(required = false) UnblockRequest request) {
        var outcome = approvals.unblockAccount(AccountId.of(id), request == null ? null : request.note());
        if (outcome.pending() != null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApprovalView.of(outcome.pending()));
        }
        return ResponseEntity.ok(new AccountUnblocked(outcome.account().id().value(), outcome.account().status().name()));
    }

    @GetMapping("/approvals")
    List<ApprovalView> list(@RequestParam(required = false) String status) {
        return approvals.list(parse(status)).stream().map(ApprovalView::of).toList();
    }

    @GetMapping("/approvals/pending-count")
    PendingCount pendingCount() {
        return new PendingCount(approvals.pendingForMe());
    }

    @PostMapping("/approvals/{id}/approve")
    ApprovalView approve(@PathVariable String id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return ApprovalView.of(approvals.approve(uuid(id), request == null ? null : request.note()));
    }

    @PostMapping("/approvals/{id}/reject")
    ApprovalView reject(@PathVariable String id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return ApprovalView.of(approvals.reject(uuid(id), request == null ? null : request.note()));
    }

    @PostMapping("/approvals/{id}/cancel")
    ApprovalView cancel(@PathVariable String id) {
        return ApprovalView.of(approvals.cancel(uuid(id)));
    }

    private static ApprovalStatus parse(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ApprovalStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("Status must be PENDING, APPROVED, REJECTED or CANCELED");
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("Invalid approval id");
        }
    }
}

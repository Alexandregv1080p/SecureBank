package com.securebank.audit.infrastructure.web;

import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.audit.domain.AuditLog;
import com.securebank.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta da trilha de auditoria (permissão VIEW_AUDIT: SUPPORT e ADMIN). Somente leitura. */
@RestController
@RequestMapping("/api/v1/audit")
class AuditController {

    record AuditLogResponse(UUID id, Instant occurredAt, String event, UUID userId, UUID accountId,
            UUID transactionId, String ip, String traceId, String detail) {

        static AuditLogResponse of(AuditLog l) {
            return new AuditLogResponse(l.id(), l.occurredAt(), l.event().name(),
                    l.userId() == null ? null : l.userId().value(),
                    l.accountId() == null ? null : l.accountId().value(),
                    l.transactionId() == null ? null : l.transactionId().value(), l.ip(), l.traceId(), l.detail());
        }
    }

    record AuditPage(List<AuditLogResponse> items, int page, int size, long totalElements) {}

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    AuditPage search(@RequestParam(required = false) AuditEvent event, @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = audit.search(event, userId == null ? null : UserId.of(userId), page, size);
        return new AuditPage(result.items().stream().map(AuditLogResponse::of).toList(), result.page(),
                result.size(), result.totalElements());
    }
}

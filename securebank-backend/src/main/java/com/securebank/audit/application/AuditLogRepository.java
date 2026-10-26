package com.securebank.audit.application;

import com.securebank.audit.domain.AuditEvent;
import com.securebank.audit.domain.AuditLog;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.UserId;

/** Só inserção e leitura: a trilha de auditoria não é editável (o banco também bloqueia UPDATE/DELETE). */
public interface AuditLogRepository {

    void save(AuditLog log);

    /** Mais recentes primeiro; evento e usuário nulos são ignorados; o período é [from, to) (use Instant.MIN/MAX-like para "sem limite"). */
    PageResult<AuditLog> search(AuditEvent event, UserId userId, java.time.Instant from, java.time.Instant to, int page, int size);
}

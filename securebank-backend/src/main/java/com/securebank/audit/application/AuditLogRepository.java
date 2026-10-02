package com.securebank.audit.application;

import com.securebank.audit.domain.AuditEvent;
import com.securebank.audit.domain.AuditLog;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.UserId;

/** Só inserção e leitura: a trilha de auditoria não é editável (o banco também bloqueia UPDATE/DELETE). */
public interface AuditLogRepository {

    void save(AuditLog log);

    /** Mais recentes primeiro; filtros nulos são ignorados. */
    PageResult<AuditLog> search(AuditEvent event, UserId userId, int page, int size);
}

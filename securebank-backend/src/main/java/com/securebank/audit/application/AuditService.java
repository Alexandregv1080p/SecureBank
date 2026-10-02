package com.securebank.audit.application;

import com.securebank.audit.domain.AuditEvent;
import com.securebank.audit.domain.AuditLog;
import com.securebank.audit.domain.IpMasker;
import com.securebank.shared.application.Actor;
import com.securebank.shared.application.ActorContext;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.UserId;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final ActorContext actors;
    private final BankTime time;
    private final MeterRegistry metrics;

    public AuditService(AuditLogRepository logs, ActorContext actors, BankTime time, MeterRegistry metrics) {
        this.logs = logs;
        this.actors = actors;
        this.time = time;
        this.metrics = metrics;
    }

    /**
     * Grava junto da transação do chamador: o evento de sucesso nasce e morre com a operação (atômico).
     * Exige transação ativa, então nunca vira escrita solta por engano.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        logs.save(toLog(entry));
        metrics.counter("securebank.audit.events", "event", entry.event().name()).increment();
    }

    /**
     * Grava numa transação própria: sobrevive ao rollback da operação. É o caminho das FALHAS
     * (transferência recusada, acesso negado), que precisam ficar registradas mesmo sem nada ter sido gravado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEntry entry) {
        logs.save(toLog(entry));
        metrics.counter("securebank.audit.events", "event", entry.event().name()).increment();
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLog> search(AuditEvent event, UserId userId, int page, int size) {
        PageResult.validate(page, size);
        return logs.search(event, userId, page, size);
    }

    private AuditLog toLog(AuditEntry entry) {
        Actor actor = actors.current();
        UserId user = entry.userId() != null ? entry.userId() : actor.userId();
        return new AuditLog(UUID.randomUUID(), time.now(), entry.event(), user, entry.accountId(),
                entry.transactionId(), IpMasker.mask(actor.ip()), actor.traceId(), entry.detail());
    }
}

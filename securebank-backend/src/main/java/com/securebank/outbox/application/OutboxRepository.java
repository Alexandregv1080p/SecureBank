package com.securebank.outbox.application;

import com.securebank.outbox.domain.OutboxEvent;
import java.time.Instant;
import java.util.List;

public interface OutboxRepository {

    void save(OutboxEvent event);

    /** Pendentes em ordem de id, travados para esta transação (instâncias concorrentes pulam os travados). */
    List<OutboxEvent> lockPending(int limit);

    void markPublished(long id, Instant now);

    void markFailed(long id);
}

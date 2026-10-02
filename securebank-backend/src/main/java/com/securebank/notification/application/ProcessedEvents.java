package com.securebank.notification.application;

import java.time.Instant;

/** Deduplicação de consumidor: registra "este consumidor já tratou este evento". */
public interface ProcessedEvents {

    /** Verdadeiro na primeira vez; falso se o evento já foi processado (reentrega). Roda na transação do chamador. */
    boolean markProcessed(String consumer, long eventId, Instant now);
}

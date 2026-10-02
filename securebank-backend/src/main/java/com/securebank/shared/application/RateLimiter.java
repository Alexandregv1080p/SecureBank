package com.securebank.shared.application;

import java.time.Duration;

/** Contador por janela de tempo (implementado em Redis, compartilhado entre instâncias da API). */
public interface RateLimiter {

    /** Registra uma ocorrência. {@code allowed} é falso quando passou de {@code limit} dentro da janela. */
    Decision hit(String key, int limit, Duration window);

    /** Ocorrências na janela atual, sem registrar nenhuma. */
    long count(String key);

    void reset(String key);

    record Decision(boolean allowed, long count, Duration retryAfter) {}
}

package com.securebank.shared.application;

import java.time.Instant;

/** Guarda o resultado da 1ª execução de uma operação para que um retry com a mesma chave receba o mesmo resultado. */
public interface IdempotencyStore {

    sealed interface Claim {
        /** Primeira vez (ou chave expirada/abandonada): execute a operação e chame {@link #complete} ou {@link #release}. */
        record Proceed() implements Claim {}

        /** Já executada com o mesmo pedido: devolva a resposta guardada. */
        record Replay(int status, String body) implements Claim {}

        /** Outra requisição com a mesma chave ainda está em andamento. */
        record InProgress() implements Claim {}

        /** A chave já foi usada com um pedido DIFERENTE: erro do cliente. */
        record Mismatch() implements Claim {}
    }

    Claim claim(String scope, String key, String fingerprint, Instant now);

    void complete(String scope, String key, int status, String body);

    /** Libera a chave (resultado não determinístico: erro 5xx, conflito de versão, limite de taxa). */
    void release(String scope, String key);
}

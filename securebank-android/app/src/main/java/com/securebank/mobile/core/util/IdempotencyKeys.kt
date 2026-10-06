package com.securebank.mobile.core.util

import java.util.UUID

/**
 * Uma Idempotency-Key por intenção. Reaproveita a chave só enquanto o pedido é idêntico E o resultado anterior foi
 * incerto (rede caiu, 5xx): assim o retry não duplica a operação. Depois de um resultado definitivo (sucesso ou erro
 * de negócio) a chave é trocada, senão a API reproduziria a resposta antiga para uma nova tentativa.
 */
class IdempotencyKeys(private val newKey: () -> String = { UUID.randomUUID().toString() }) {
    private var key: String? = null
    private var fingerprint: String? = null

    /** [payload] deve ter toString() estável (data class). */
    @Synchronized
    fun keyFor(payload: Any): String {
        val current = payload.toString()
        if (key == null || fingerprint != current) {
            key = newKey()
            fingerprint = current
        }
        return key!!
    }

    /** Chame quando a resposta foi definitiva. */
    @Synchronized
    fun settle() {
        key = null
        fingerprint = null
    }
}

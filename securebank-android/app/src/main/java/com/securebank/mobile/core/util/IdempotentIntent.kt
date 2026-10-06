package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.ApiError
import kotlin.coroutines.cancellation.CancellationException

/**
 * Uma operação de dinheiro com a regra de idempotência do web: a MESMA Idempotency-Key é reaproveitada só enquanto o
 * pedido é idêntico E o resultado anterior foi incerto (rede caiu, 5xx, 409, 429); a API então devolve a resposta
 * original em vez de repetir a operação. Depois de sucesso ou de um erro de negócio (saldo insuficiente...) a chave é
 * trocada: senão uma nova tentativa receberia o replay da resposta antiga.
 */
class IdempotentIntent(private val keys: IdempotencyKeys = IdempotencyKeys()) {

    /** [payload] precisa ter toString() estável (data class). */
    suspend fun <T> run(payload: Any, call: suspend (key: String) -> T): T {
        val key = keys.keyFor(payload)
        try {
            val result = call(key)
            keys.settle()
            return result
        } catch (e: CancellationException) {
            throw e // a requisição pode ter saído: mantém a chave
        } catch (e: Exception) {
            if (e !is ApiError || !e.uncertain) keys.settle()
            throw e
        }
    }
}

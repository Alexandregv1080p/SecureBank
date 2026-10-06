package com.securebank.mobile.data

import com.securebank.mobile.core.network.Page
import com.securebank.mobile.core.network.PixApi
import com.securebank.mobile.core.network.PixEntry
import com.securebank.mobile.core.network.PixKey
import com.securebank.mobile.core.network.PixLookup
import com.securebank.mobile.core.network.RegisterPixKeyRequest
import com.securebank.mobile.core.network.SendPixRequest
import com.securebank.mobile.core.network.apiCall
import kotlinx.serialization.json.Json

/** Pix. Qualquer mudança avisa as telas pelo [BankingRepository.changes] (saldo, extrato e histórico recarregam juntos). */
class PixRepository(private val api: PixApi, private val json: Json, private val banking: BankingRepository) {

    suspend fun keys(): List<PixKey> = apiCall(json) { api.keys() }

    suspend fun registerKey(accountId: String, type: String): PixKey =
        apiCall(json) { api.registerKey(RegisterPixKeyRequest(accountId, type)) }.also { banking.notifyChanged() }

    suspend fun deleteKey(id: String) {
        apiCall(json) { api.deleteKey(id) }
        banking.notifyChanged()
    }

    /** Resolve a chave para nome/CPF mascarados (limitado por taxa no servidor). 404 = chave inexistente. */
    suspend fun lookup(key: String): PixLookup = apiCall(json) { api.lookup(key.trim()) }

    /** [key]: Idempotency-Key da intenção (ver IdempotentIntent). */
    suspend fun send(sourceAccountId: String, pixKey: String, amount: String, message: String?, key: String): PixEntry =
        apiCall(json) { api.send(SendPixRequest(sourceAccountId, pixKey.trim(), amount, message), key) }
            .also { banking.notifyChanged() }

    suspend fun history(page: Int = 0, size: Int = 20): Page<PixEntry> = apiCall(json) { api.history(page, size) }
}

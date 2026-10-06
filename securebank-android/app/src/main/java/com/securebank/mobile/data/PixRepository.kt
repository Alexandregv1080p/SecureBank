package com.securebank.mobile.data

import com.securebank.mobile.core.network.CreateChargeRequest
import com.securebank.mobile.core.network.CreateScheduleRequest
import com.securebank.mobile.core.network.Page
import com.securebank.mobile.core.network.PayChargeRequest
import com.securebank.mobile.core.network.PixApi
import com.securebank.mobile.core.network.PixCharge
import com.securebank.mobile.core.network.PixChargeView
import com.securebank.mobile.core.network.PixEntry
import com.securebank.mobile.core.network.PixKey
import com.securebank.mobile.core.network.PixLookup
import com.securebank.mobile.core.network.PixScheduleDto
import com.securebank.mobile.core.network.RefundRequest
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

    suspend fun transfer(id: String): PixEntry = apiCall(json) { api.transfer(id) }

    /** Devolve (parte de) um Pix recebido. [amount] nulo = devolve o que ainda resta. */
    suspend fun refund(id: String, amount: String?, key: String): PixEntry =
        apiCall(json) { api.refund(id, RefundRequest(amount), key) }.also { banking.notifyChanged() }

    // ---------------------------------------------------------------- cobranças (QR dinâmico)
    suspend fun createCharge(accountId: String, amount: String, description: String?, expiresInMinutes: Int?): PixCharge =
        apiCall(json) { api.createCharge(CreateChargeRequest(accountId, amount, description, expiresInMinutes)) }
            .also { banking.notifyChanged() }

    suspend fun charges(page: Int = 0, size: Int = 20): Page<PixCharge> = apiCall(json) { api.charges(page, size) }

    /** O pagador consulta a cobrança pelo txid que veio do QR (a API só aceita o formato do BR Code). */
    suspend fun charge(txid: String): PixChargeView = apiCall(json) { api.charge(txid) }

    suspend fun cancelCharge(txid: String) {
        apiCall(json) { api.cancelCharge(txid) }
        banking.notifyChanged()
    }

    suspend fun payCharge(txid: String, sourceAccountId: String, key: String): PixEntry =
        apiCall(json) { api.payCharge(txid, PayChargeRequest(sourceAccountId), key) }.also { banking.notifyChanged() }

    // ---------------------------------------------------------------- agendados
    /** [scheduledFor]: dia no formato ISO (2026-10-20). Nada se move agora; o dinheiro sai na data. */
    suspend fun schedule(sourceAccountId: String, pixKey: String, amount: String, message: String?, scheduledFor: String, key: String): PixScheduleDto =
        apiCall(json) { api.schedule(CreateScheduleRequest(sourceAccountId, pixKey.trim(), amount, message, scheduledFor), key) }
            .also { banking.notifyChanged() }

    suspend fun schedules(page: Int = 0, size: Int = 20): Page<PixScheduleDto> = apiCall(json) { api.schedules(page, size) }

    suspend fun cancelSchedule(id: String) {
        apiCall(json) { api.cancelSchedule(id) }
        banking.notifyChanged()
    }
}

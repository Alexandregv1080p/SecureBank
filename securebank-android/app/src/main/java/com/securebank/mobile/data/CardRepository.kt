package com.securebank.mobile.data

import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.network.CardApi
import com.securebank.mobile.core.network.CardDetails
import com.securebank.mobile.core.network.CardPurchase
import com.securebank.mobile.core.network.CardPurchaseRequest
import com.securebank.mobile.core.network.CardReasonRequest
import com.securebank.mobile.core.network.IssueCardRequest
import com.securebank.mobile.core.network.apiCall
import kotlinx.serialization.json.Json

/**
 * Cartões de débito. Emitir, bloquear, cancelar e comprar avisam as telas pelo [BankingRepository.changes]: a compra mexe
 * no saldo da conta e no extrato, então Início, Contas e cartões recarregam juntos.
 */
class CardRepository(private val api: CardApi, private val json: Json, private val banking: BankingRepository) {

    suspend fun list(): List<Card> = apiCall(json) { api.list() }

    suspend fun get(id: String): Card = apiCall(json) { api.get(id) }

    suspend fun issue(accountId: String): Card =
        apiCall(json) { api.issue(IssueCardRequest(accountId)) }.also { banking.notifyChanged() }

    /** Número completo, validade e CVV: cada consulta fica na auditoria do servidor. */
    suspend fun reveal(id: String): CardDetails = apiCall(json) { api.reveal(id) }

    /** O motivo é obrigatório: fica no cartão e na auditoria. */
    suspend fun block(id: String, reason: String): Card = apiCall(json) { api.block(id, CardReasonRequest(reason)) }.also { banking.notifyChanged() }

    suspend fun unblock(id: String): Card = apiCall(json) { api.unblock(id) }.also { banking.notifyChanged() }

    /** Definitivo: o cartão cancelado não volta. */
    suspend fun cancel(id: String, reason: String): Card = apiCall(json) { api.cancel(id, CardReasonRequest(reason)) }.also { banking.notifyChanged() }

    /** [key]: Idempotency-Key da intenção (a mesma compra repetida depois de uma falha de rede não cobra duas vezes). */
    suspend fun purchase(id: String, amount: String, merchant: String, key: String): CardPurchase =
        apiCall(json) { api.purchase(id, CardPurchaseRequest(amount, merchant.trim()), key) }.also { banking.notifyChanged() }

    suspend fun purchases(id: String): List<CardPurchase> = apiCall(json) { api.purchases(id) }.items
}

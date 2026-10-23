package com.securebank.mobile.data

import com.securebank.mobile.core.network.AdditionalCardRequest
import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.network.LimitRequest
import com.securebank.mobile.core.network.RefinanceOffer
import com.securebank.mobile.core.network.RefinanceRequest
import com.securebank.mobile.core.network.Refinanced
import com.securebank.mobile.core.network.CreditApi
import com.securebank.mobile.core.network.CreditPurchase
import com.securebank.mobile.core.network.CreditPurchaseRequest
import com.securebank.mobile.core.network.CreditSummary
import com.securebank.mobile.core.network.Invoice
import com.securebank.mobile.core.network.InvoiceDetail
import com.securebank.mobile.core.network.IssueCreditRequest
import com.securebank.mobile.core.network.PayInvoiceRequest
import com.securebank.mobile.core.network.apiCall
import kotlinx.serialization.json.Json

/**
 * Cartão de crédito: limite, compras parceladas, faturas e pagamento. Emitir e pagar mexem no extrato da conta; por isso avisam
 * o [BankingRepository.changes] e as telas recarregam juntas. Número, CVV, bloquear e cancelar são os do [CardRepository].
 */
class CreditRepository(private val api: CreditApi, private val json: Json, private val banking: BankingRepository) {

    suspend fun issue(accountId: String, dueDay: Int): Card =
        apiCall(json) { api.issue(IssueCreditRequest(accountId, dueDay)) }.also { banking.notifyChanged() }

    suspend fun addAdditional(mainId: String, holderName: String): Card =
        apiCall(json) { api.additional(mainId, AdditionalCardRequest(holderName.trim())) }.also { banking.notifyChanged() }

    suspend fun changeLimit(id: String, limit: String): CreditSummary =
        apiCall(json) { api.changeLimit(id, LimitRequest(limit)) }.also { banking.notifyChanged() }

    suspend fun refinanceOptions(id: String, month: String): RefinanceOffer = apiCall(json) { api.refinanceOptions(id, month) }

    suspend fun refinance(id: String, month: String, installments: Int, key: String): Refinanced =
        apiCall(json) { api.refinance(id, month, RefinanceRequest(installments), key) }.also { banking.notifyChanged() }

    suspend fun summary(id: String): CreditSummary = apiCall(json) { api.summary(id) }

    suspend fun invoices(id: String): List<Invoice> = apiCall(json) { api.invoices(id) }

    suspend fun invoice(id: String, month: String): InvoiceDetail = apiCall(json) { api.invoice(id, month) }

    suspend fun purchases(id: String): List<CreditPurchase> = apiCall(json) { api.purchases(id) }.items

    /** [key]: Idempotency-Key da intenção (a mesma compra repetida depois de uma falha de rede não usa o limite duas vezes). */
    suspend fun purchase(id: String, amount: String, merchant: String, installments: Int, key: String): CreditPurchase =
        apiCall(json) { api.purchase(id, CreditPurchaseRequest(amount, merchant.trim(), installments), key) }.also { banking.notifyChanged() }

    suspend fun payInvoice(id: String, month: String, amount: String, key: String): Invoice =
        apiCall(json) { api.payInvoice(id, month, PayInvoiceRequest(amount), key) }.also { banking.notifyChanged() }
}

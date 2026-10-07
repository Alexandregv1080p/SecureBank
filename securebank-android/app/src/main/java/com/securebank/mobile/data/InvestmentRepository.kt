package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApplyInvestmentRequest
import com.securebank.mobile.core.network.Investment
import com.securebank.mobile.core.network.InvestmentApi
import com.securebank.mobile.core.network.InvestmentProduct
import com.securebank.mobile.core.network.RedeemInvestmentRequest
import com.securebank.mobile.core.network.apiCall
import kotlinx.serialization.json.Json

/** Investimentos. Aplicar e resgatar mexem no saldo, então avisam as telas pelo [BankingRepository.changes]. */
class InvestmentRepository(private val api: InvestmentApi, private val json: Json, private val banking: BankingRepository) {

    suspend fun products(): List<InvestmentProduct> = apiCall(json) { api.products() }

    suspend fun list(): List<Investment> = apiCall(json) { api.list() }

    suspend fun get(id: String): Investment = apiCall(json) { api.get(id) }

    /** [key]: Idempotency-Key da intenção (ver IdempotentIntent). */
    suspend fun apply(accountId: String, productCode: String, amount: String, key: String): Investment =
        apiCall(json) { api.apply(ApplyInvestmentRequest(accountId, productCode, amount), key) }.also { banking.notifyChanged() }

    /** [amount] nulo resgata tudo; com valor, resgata esse líquido (se for o total ou mais, resgata tudo). */
    suspend fun redeem(id: String, key: String, amount: String? = null): Investment =
        apiCall(json) { api.redeem(id, RedeemInvestmentRequest(amount), key) }.also { banking.notifyChanged() }
}

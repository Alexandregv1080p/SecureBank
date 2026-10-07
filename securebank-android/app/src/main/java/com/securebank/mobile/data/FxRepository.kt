package com.securebank.mobile.data

import com.securebank.mobile.core.network.FxApi
import com.securebank.mobile.core.network.FxOperation
import com.securebank.mobile.core.network.FxRate
import com.securebank.mobile.core.network.FxTradeRequest
import com.securebank.mobile.core.network.FxWallet
import com.securebank.mobile.core.network.Page
import com.securebank.mobile.core.network.apiCall
import kotlinx.serialization.json.Json

/** Câmbio simulado. Comprar e vender mexem no saldo, então avisam as telas pelo [BankingRepository.changes]. */
class FxRepository(private val api: FxApi, private val json: Json, private val banking: BankingRepository) {

    suspend fun rates(): List<FxRate> = apiCall(json) { api.rates() }

    suspend fun wallets(): List<FxWallet> = apiCall(json) { api.wallets() }

    suspend fun operations(page: Int = 0, size: Int = 10): Page<FxOperation> = apiCall(json) { api.operations(page, size) }

    /** Compra [amount] da moeda pela cotação [quotedRate] que o cliente viu. [key]: Idempotency-Key da intenção. */
    suspend fun buy(accountId: String, currency: String, amount: String, quotedRate: String, key: String): FxOperation =
        apiCall(json) { api.buy(FxTradeRequest(accountId, currency, amount, quotedRate), key) }.also { banking.notifyChanged() }

    suspend fun sell(accountId: String, currency: String, amount: String, quotedRate: String, key: String): FxOperation =
        apiCall(json) { api.sell(FxTradeRequest(accountId, currency, amount, quotedRate), key) }.also { banking.notifyChanged() }
}

package com.securebank.mobile.data

import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.BankingApi
import com.securebank.mobile.core.network.Customer
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.OpenAccountRequest
import com.securebank.mobile.core.network.Page
import com.securebank.mobile.core.network.Transaction
import com.securebank.mobile.core.network.apiCall
import java.time.LocalDate
import kotlinx.serialization.json.Json

/** Leituras e abertura de conta. Toda falha chega como ApiError (ver apiCall). */
class BankingRepository(private val api: BankingApi, private val json: Json) {
    suspend fun me(): Customer = apiCall(json) { api.me() }

    suspend fun accounts(): List<Account> = apiCall(json) { api.accounts() }

    suspend fun account(id: String): Account = apiCall(json) { api.account(id) }

    suspend fun limits(id: String): List<LimitUsage> = apiCall(json) { api.limits(id) }

    /** Extrato, mais recente primeiro. [from]/[to] são dias (a API usa o dia de America/Sao_Paulo). */
    suspend fun statement(
        id: String,
        page: Int,
        size: Int = STATEMENT_PAGE_SIZE,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ): Page<Transaction> = apiCall(json) { api.statement(id, page, size, from?.toString(), to?.toString()) }

    suspend fun openAccount(type: String): Account = apiCall(json) { api.openAccount(OpenAccountRequest(type)) }

    companion object {
        const val STATEMENT_PAGE_SIZE = 20
    }
}

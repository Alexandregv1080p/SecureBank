package com.securebank.mobile.data

import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.AmountRequest
import com.securebank.mobile.core.network.AppNotification
import com.securebank.mobile.core.network.BankingApi
import com.securebank.mobile.core.network.Customer
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.OpenAccountRequest
import com.securebank.mobile.core.network.Page
import com.securebank.mobile.core.network.Payment
import com.securebank.mobile.core.network.PaymentRequest
import com.securebank.mobile.core.network.Transaction
import com.securebank.mobile.core.network.Transfer
import com.securebank.mobile.core.network.TransferRequest
import com.securebank.mobile.core.network.apiCall
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

/**
 * Leituras e operações de dinheiro. Toda falha chega como ApiError (ver apiCall). Depois de qualquer operação que mexe em
 * saldo, [changes] avisa as telas abertas (início, contas, extrato, seletores) para recarregarem.
 */
class BankingRepository(private val api: BankingApi, private val json: Json) {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Para quem mexe em saldo por outro caminho (porquinhos): avisa as telas para recarregarem. */
    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

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
        category: String? = null,
        direction: String? = null,
    ): Page<Transaction> = apiCall(json) { api.statement(id, page, size, from?.toString(), to?.toString(), category, direction) }

    suspend fun statementSummary(id: String, month: java.time.YearMonth): com.securebank.mobile.core.network.StatementSummary =
        apiCall(json) { api.statementSummary(id, month.toString()) }

    /** CSV do extrato (até 5.000 lançamentos), com os mesmos filtros da tela. */
    suspend fun exportStatement(
        id: String,
        from: LocalDate? = null,
        to: LocalDate? = null,
        category: String? = null,
        direction: String? = null,
    ): String = apiCall(json) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            api.exportStatement(id, from?.toString(), to?.toString(), category, direction).use { it.string() }
        }
    }

    suspend fun openAccount(type: String): Account =
        apiCall(json) { api.openAccount(OpenAccountRequest(type)) }.also { _changes.tryEmit(Unit) }

    /** [key]: Idempotency-Key da intenção (ver IdempotentIntent). */
    suspend fun deposit(accountId: String, amount: String, key: String): Transaction =
        apiCall(json) { api.deposit(accountId, AmountRequest(amount), key) }.also { _changes.tryEmit(Unit) }

    suspend fun withdraw(accountId: String, amount: String, key: String): Transaction =
        apiCall(json) { api.withdraw(accountId, AmountRequest(amount), key) }.also { _changes.tryEmit(Unit) }

    suspend fun transfer(request: TransferRequest, key: String): Transfer =
        apiCall(json) { api.transfer(request, key) }.also { _changes.tryEmit(Unit) }

    suspend fun pay(request: PaymentRequest, key: String): Payment =
        apiCall(json) { api.pay(request, key) }.also { _changes.tryEmit(Unit) }

    suspend fun payments(page: Int = 0): Page<Payment> = apiCall(json) { api.payments(page) }

    suspend fun notifications(page: Int = 0, size: Int = 20): Page<AppNotification> = apiCall(json) { api.notifications(page, size) }

    suspend fun markRead(id: String) {
        apiCall(json) { api.markRead(id) }
    }

    companion object {
        const val STATEMENT_PAGE_SIZE = 20
    }
}

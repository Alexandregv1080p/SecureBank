package com.securebank.mobile.ui.money

import androidx.lifecycle.ViewModel
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.util.Validation
import com.securebank.mobile.ui.attempt
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.OperationValidation
import com.securebank.mobile.data.BankingRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MoneyKind { DEPOSIT, WITHDRAW }

data class MoneyState(
    val amount: String = "",
    val amountError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: String? = null,
    /** Saldo e limite de saque (para conferir antes da biometria e mostrar na tela). */
    val balance: String? = null,
    val limit: LimitUsage? = null,
)

/** Depósito ou saque de uma conta. */
class MoneyViewModel(
    private val banking: BankingRepository,
    private val accountId: String,
    val kind: MoneyKind,
) : ViewModel() {
    private val _state = MutableStateFlow(MoneyState())
    val state: StateFlow<MoneyState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    init {
        viewModelScope.launch {
            val account = attempt { banking.account(accountId) }.getOrNull()
            val limit = attempt { banking.limits(accountId) }.getOrNull()?.firstOrNull { it.type == "WITHDRAW" }
            _state.update { it.copy(balance = account?.balance?.amount, limit = limit) }
        }
    }

    private data class Payload(val accountId: String, val kind: MoneyKind, val value: String)

    fun onAmount(raw: String) {
        val value = Validation.sanitizeAmount(raw)
        _state.update { it.copy(amount = value, amountError = null, error = null, done = null) }
    }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val value = Money.parse(s.amount)
        val issue = Validation.amountIssue(s.amount)
            ?: if (kind == MoneyKind.WITHDRAW) value?.let { v -> s.balance?.let { Validation.balanceIssue(v, it) } ?: Validation.limitIssue(v, s.limit) } else null
        if (value == null || issue != null) {
            _state.update { it.copy(amountError = issue ?: OperationValidation.AMOUNT_MESSAGE) }
            return
        }
        _state.update { it.copy(loading = true, error = null, done = null) }
        viewModelScope.launch {
            try {
                intent.run(Payload(accountId, kind, value)) { key ->
                    if (kind == MoneyKind.DEPOSIT) banking.deposit(accountId, value, key) else banking.withdraw(accountId, value, key)
                }
                val message = if (kind == MoneyKind.DEPOSIT) "Depósito de ${Money.format(value)} realizado." else "Saque de ${Money.format(value)} realizado."
                _state.update { it.copy(amount = "", done = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

package com.securebank.mobile.ui.fx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.FxOperation
import com.securebank.mobile.core.network.FxRate
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.FxValidation
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.FxRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FxTradeState(
    val rate: Load<FxRate> = Load.Loading,
    /** Saldo da carteira desta moeda (para vender). */
    val walletBalance: String = "0.00",
    val accounts: Load<List<Account>> = Load.Loading,
    val accountId: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: FxOperation? = null,
    /** Limite de câmbio da conta escolhida (a compra sai dela em reais). */
    val limit: com.securebank.mobile.core.network.LimitUsage? = null,
)

/** Comprar ou vender uma moeda. Manda ao servidor a cotação que a pessoa VIU: se mudou, ele recusa e a tela mostra a nova. */
class FxTradeViewModel(
    private val repository: FxRepository,
    private val banking: BankingRepository,
    val buying: Boolean,
    private val currency: String,
) : ViewModel() {
    private val _state = MutableStateFlow(FxTradeState())
    val state: StateFlow<FxTradeState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    private data class Payload(val account: String, val currency: String, val amount: String, val rate: String, val buying: Boolean)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val rate = attempt { repository.rates().first { it.currency == currency } }
            val wallet = attempt { repository.wallets().firstOrNull { it.currency == currency }?.balance?.amount ?: "0.00" }
            val accounts = attempt { banking.accounts() }
            _state.update { s ->
                s.copy(
                    rate = rate.fold({ Load.Ready(it) }, { Load.Failed(messageFor(it)) }),
                    walletBalance = wallet.getOrDefault(s.walletBalance),
                    accounts = accounts.fold({ Load.Ready(it) }, { Load.Failed(messageFor(it)) }),
                    accountId = s.accountId ?: accounts.getOrNull()?.singleOrNull()?.id,
                )
            }
            _state.value.accountId?.let { loadLimit(it) }
        }
    }

    private fun loadLimit(accountId: String) {
        viewModelScope.launch {
            val limit = attempt { banking.limits(accountId) }.getOrNull()?.firstOrNull { it.type == "FX" }
            _state.update { if (it.accountId == accountId) it.copy(limit = limit) else it }
        }
    }

    fun onAccount(id: String) {
        _state.update { it.copy(accountId = id, limit = null, error = null) }
        loadLimit(id)
    }

    fun onAmount(v: String) = _state.update { it.copy(amount = com.securebank.mobile.core.util.Validation.sanitizeAmount(v), amountError = null, error = null) }
    fun sellAll() = _state.update { it.copy(amount = it.walletBalance.replace('.', ','), amountError = null) }

    /** Cotação aplicada a esta operação (a que a pessoa está vendo). */
    fun appliedRate(s: FxTradeState): String? = (s.rate as? Load.Ready)?.value?.let { if (buying) it.buyRate else it.sellRate }

    /** Estimativa em reais do que a pessoa paga (compra) ou recebe (venda). */
    fun estimate(s: FxTradeState): String? {
        val rate = appliedRate(s) ?: return null
        return if (buying) FxValidation.buyCost(s.amount, rate) else FxValidation.sellProceeds(s.amount, rate)
    }

    /** Valida; a tela pede a biometria e só então chama [confirm]. */
    fun validate(): Boolean {
        val s = _state.value
        var error = FxValidation.amountError(s.amount, if (buying) null else s.walletBalance)
        if (error == null && buying) {
            // a compra sai da conta em reais: confere saldo e limite de câmbio antes da biometria
            val cost = estimate(s)
            val account = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == s.accountId }
            if (cost != null) {
                error = account?.let { com.securebank.mobile.core.util.Validation.balanceIssue(cost, it.balance.amount) }
                    ?: com.securebank.mobile.core.util.Validation.limitIssue(cost, s.limit)
            }
        }
        _state.update { it.copy(amountError = error, error = if (s.accountId == null) "Escolha a conta." else null) }
        return error == null && s.accountId != null
    }

    /** Só chamar DEPOIS da confirmação de identidade. */
    fun confirm() {
        val s = _state.value
        if (s.loading) return
        val amount = Money.parse(s.amount) ?: return
        val account = s.accountId ?: return
        val rate = appliedRate(s) ?: return
        val payload = Payload(account, currency, amount, rate, buying)
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val op = intent.run(payload) { key ->
                    if (buying) repository.buy(account, currency, amount, rate, key) else repository.sell(account, currency, amount, rate, key)
                }
                _state.update { it.copy(done = op) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
                if (e is ApiError && e.code == "FX_RATE_CHANGED") load() // mostra a cotação nova para a pessoa decidir
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

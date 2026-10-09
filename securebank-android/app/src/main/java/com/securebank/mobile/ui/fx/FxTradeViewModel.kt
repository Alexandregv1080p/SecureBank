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

enum class FxStep { Form, Review }

/** Quanto tempo a cotação da revisão vale antes de ser conferida de novo. */
const val QUOTE_SECONDS = 30

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
    val step: FxStep = FxStep.Form,
    /** A cotação que a pessoa está vendo na revisão e quantos segundos ela ainda vale. */
    val reviewRate: String? = null,
    val secondsLeft: Int = 0,
    val notice: String? = null,
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
    private var timer: kotlinx.coroutines.Job? = null

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

    /** A cotação que vale agora para a conta: a da revisão, se há uma aberta. */
    private fun shownRate(s: FxTradeState): String? = s.reviewRate ?: appliedRate(s)

    /** Estimativa em reais do que a pessoa paga (compra) ou recebe (venda). */
    fun estimate(s: FxTradeState): String? {
        val rate = shownRate(s) ?: return null
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

    /** Valida e abre a revisão com a cotação mais recente; depois a cotação é conferida de novo a cada [QUOTE_SECONDS] segundos. */
    fun review() {
        if (_state.value.loading || !validate()) return
        _state.update { it.copy(loading = true, error = null, notice = null) }
        viewModelScope.launch {
            val shown = appliedRate(_state.value)
            val fresh = attempt { repository.rates().first { it.currency == currency } }.getOrNull()
            val current = fresh?.let { if (buying) it.buyRate else it.sellRate } ?: shown
            _state.update {
                it.copy(
                    loading = false, step = FxStep.Review, reviewRate = current, secondsLeft = QUOTE_SECONDS,
                    rate = fresh?.let { r -> Load.Ready(r) } ?: it.rate,
                    notice = if (shown != null && current != null && shown != current) changedNotice(shown, current) else null,
                )
            }
            startTimer()
        }
    }

    private fun changedNotice(from: String, to: String) =
        "A cotação mudou de R$ ${FxValidation.rateLabel(from)} para R$ ${FxValidation.rateLabel(to)}. Confira o novo valor."

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            while (_state.value.step == FxStep.Review) {
                for (left in QUOTE_SECONDS downTo 1) {
                    _state.update { it.copy(secondsLeft = left) }
                    kotlinx.coroutines.delay(1000)
                }
                if (_state.value.loading) continue // enviando: não troca a cotação debaixo da confirmação
                val previous = _state.value.reviewRate
                val fresh = attempt { repository.rates().first { it.currency == currency } }.getOrNull() ?: continue
                val next = if (buying) fresh.buyRate else fresh.sellRate
                _state.update {
                    it.copy(
                        reviewRate = next, rate = Load.Ready(fresh),
                        notice = if (previous != null && previous != next) changedNotice(previous, next) else it.notice,
                    )
                }
            }
        }
    }

    fun backToForm() {
        timer?.cancel()
        _state.update { it.copy(step = FxStep.Form, reviewRate = null, notice = null, error = null) }
    }

    /** Só chamar DEPOIS da confirmação de identidade. */
    fun confirm() {
        val s = _state.value
        if (s.loading) return
        val amount = Money.parse(s.amount) ?: return
        val account = s.accountId ?: return
        val rate = s.reviewRate ?: return
        val payload = Payload(account, currency, amount, rate, buying)
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val op = intent.run(payload) { key ->
                    if (buying) repository.buy(account, currency, amount, rate, key) else repository.sell(account, currency, amount, rate, key)
                }
                timer?.cancel()
                _state.update { it.copy(done = op) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
                if (e is ApiError && e.code == "FX_RATE_CHANGED") {
                    // a cotação mudou no meio do caminho: mostra a nova para a pessoa decidir de novo
                    val fresh = attempt { repository.rates().first { it.currency == currency } }.getOrNull()
                    if (fresh != null) {
                        val next = if (buying) fresh.buyRate else fresh.sellRate
                        _state.update { it.copy(reviewRate = next, rate = Load.Ready(fresh), secondsLeft = QUOTE_SECONDS, notice = changedNotice(rate, next)) }
                    }
                }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

package com.securebank.mobile.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.network.CardDetails
import com.securebank.mobile.core.network.CardPurchase
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.CardValidation
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.Validation
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.CardRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Quanto tempo o número e o CVV ficam na tela antes de se esconderem sozinhos. */
const val REVEAL_SECONDS = 30

enum class CardAction { BLOCK, UNBLOCK, CANCEL }

data class CardDetailState(
    val card: Load<Card> = Load.Loading,
    val account: Account? = null,
    val limit: LimitUsage? = null,
    val purchases: List<CardPurchase> = emptyList(),
    val details: CardDetails? = null,
    val secondsLeft: Int = 0,
    val merchant: String = "",
    val amount: String = "",
    val merchantError: String? = null,
    val amountError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

class CardDetailViewModel(
    private val repository: CardRepository,
    private val banking: BankingRepository,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow(CardDetailState())
    val state: StateFlow<CardDetailState> = _state.asStateFlow()
    private val intent = IdempotentIntent()
    private var countdown: Job? = null

    private data class Payload(val id: String, val amount: String, val merchant: String)

    init {
        load()
        viewModelScope.launch { banking.changes.collect { reload() } }
    }

    fun load() {
        _state.update { it.copy(card = Load.Loading) }
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val card = attempt { repository.get(id) }
            val c = card.getOrNull()
            val account = c?.let { x -> attempt { banking.accounts() }.getOrNull()?.firstOrNull { it.id == x.accountId } }
            val limit = c?.let { attempt { banking.limits(it.accountId) }.getOrNull()?.firstOrNull { l -> l.type == "CARD" } }
            val purchases = attempt { repository.purchases(id) }.getOrNull().orEmpty()
            _state.update { it.copy(card = card.toLoad(), account = account, limit = limit, purchases = purchases) }
        }
    }

    fun onMerchant(v: String) = _state.update { it.copy(merchant = v.take(CardValidation.MERCHANT_MAX + 5), merchantError = null, error = null, done = null) }

    fun onAmount(raw: String) = _state.update { it.copy(amount = Validation.sanitizeAmount(raw), amountError = null, error = null, done = null) }

    /** Mostra número e CVV por [REVEAL_SECONDS] segundos; a tela pede a biometria antes de chamar. */
    fun reveal() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val details = repository.reveal(id)
                _state.update { it.copy(details = details, secondsLeft = REVEAL_SECONDS) }
                startCountdown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun hide() {
        countdown?.cancel()
        _state.update { it.copy(details = null, secondsLeft = 0) }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (_state.value.secondsLeft > 0) {
                delay(1000)
                _state.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            _state.update { it.copy(details = null) }
        }
    }

    /** [reason] é obrigatório para bloquear e cancelar (a tela já o pediu); desbloquear não precisa. */
    fun change(action: CardAction, reason: String? = null) {
        if (_state.value.loading) return
        hide()
        runCatchingUi {
            when (action) {
                CardAction.BLOCK -> repository.block(id, reason.orEmpty())
                CardAction.UNBLOCK -> repository.unblock(id)
                CardAction.CANCEL -> repository.cancel(id, reason.orEmpty())
            }
            _state.update { it.copy(done = when (action) { CardAction.BLOCK -> "Cartão bloqueado."; CardAction.UNBLOCK -> "Cartão desbloqueado."; CardAction.CANCEL -> "Cartão cancelado." }) }
        }
    }

    /** Compra simulada no débito; a tela pede a biometria antes de chamar. */
    fun purchase() {
        val s = _state.value
        if (s.loading) return
        val merchantError = CardValidation.merchantIssue(s.merchant)
        val amountError = CardValidation.amountIssue(s.amount, s.account?.balance?.amount, s.limit)
        if (merchantError != null || amountError != null) {
            _state.update { it.copy(merchantError = merchantError, amountError = amountError) }
            return
        }
        val value = Money.parse(s.amount)!!
        val name = s.merchant.trim()
        runCatchingUi {
            intent.run(Payload(id, value, name)) { key -> repository.purchase(id, value, name, key) }
            _state.update { it.copy(amount = "", merchant = "", done = "Compra de ${Money.format(value)} em $name aprovada.") }
        }
    }

    private fun runCatchingUi(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null, done = null) }
        viewModelScope.launch {
            try {
                block()
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

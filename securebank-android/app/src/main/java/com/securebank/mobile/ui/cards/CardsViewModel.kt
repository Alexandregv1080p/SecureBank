package com.securebank.mobile.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.CardValidation
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.CardRepository
import com.securebank.mobile.data.CreditRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardsState(
    val cards: Load<List<Card>> = Load.Loading,
    val accounts: Load<List<Account>> = Load.Loading,
    val accountId: String? = null,
    /** DEBIT ou CREDIT: o tipo que o cliente quer pedir. */
    val kind: String = "DEBIT",
    val dueDay: Int = 10,
    val issuing: Boolean = false,
    val error: String? = null,
    val accountError: String? = null,
    /** Cartão recém-emitido: a tela abre os detalhes dele. */
    val issuedId: String? = null,
)

class CardsViewModel(
    private val repository: CardRepository,
    private val credit: CreditRepository,
    private val banking: BankingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CardsState())
    val state: StateFlow<CardsState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { reload() } }
    }

    fun load() {
        _state.update { it.copy(cards = Load.Loading, accounts = Load.Loading) }
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val cards = attempt { repository.list() }
            val accounts = attempt { banking.accounts() }
            _state.update { it.copy(cards = cards.toLoad(), accounts = accounts.toLoad()) }
        }
    }

    fun onAccount(id: String) = _state.update { it.copy(accountId = id, accountError = null, error = null) }

    fun onKind(kind: String) = _state.update { it.copy(kind = kind, error = null) }

    fun onDueDay(day: Int) = _state.update { it.copy(dueDay = day) }

    fun consumeIssued() = _state.update { it.copy(issuedId = null) }

    /** Contas ativas onde ainda cabe um cartão (até [CardValidation.MAX_OPEN_PER_ACCOUNT] por conta). */
    fun openOn(accountId: String): Int =
        (_state.value.cards as? Load.Ready)?.value?.count { it.accountId == accountId && it.kind == _state.value.kind } ?: 0

    fun issue() {
        val s = _state.value
        if (s.issuing) return
        val active = (s.accounts as? Load.Ready)?.value.orEmpty().filter { it.status == "ACTIVE" }
        val accountId = s.accountId ?: active.singleOrNull()?.id
        if (accountId == null) {
            _state.update { it.copy(accountError = "Escolha a conta do cartão") }
            return
        }
        if (openOn(accountId) >= CardValidation.MAX_OPEN_PER_ACCOUNT) {
            _state.update { it.copy(error = "Esta conta já tem ${CardValidation.MAX_OPEN_PER_ACCOUNT} cartões desse tipo. Cancele um para pedir outro.") }
            return
        }
        _state.update { it.copy(issuing = true, error = null) }
        viewModelScope.launch {
            try {
                val card = if (s.kind == "CREDIT") credit.issue(accountId, s.dueDay) else repository.issue(accountId)
                _state.update { it.copy(issuedId = card.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(issuing = false) }
            }
        }
    }
}

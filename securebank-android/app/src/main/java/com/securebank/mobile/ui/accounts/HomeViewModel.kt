package com.securebank.mobile.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.PiggyRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val firstName: String? = null,
    val accounts: Load<List<Account>> = Load.Loading,
    /** Resumo na tela inicial: se falhar, a seção só não aparece (não é crítica). */
    val piggies: List<Piggy> = emptyList(),
    val refreshing: Boolean = false,
)

class HomeViewModel(private val banking: BankingRepository, private val piggies: PiggyRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { load() } } // depois de operar, o saldo já aparece novo
    }

    fun refresh() = load()

    private fun load() {
        _state.update { if (it.accounts is Load.Ready) it.copy(refreshing = true) else it.copy(accounts = Load.Loading) }
        viewModelScope.launch {
            val (accounts, me, savings) = coroutineScope {
                val a = async { attempt { banking.accounts() } }
                val m = async { attempt { banking.me() } }
                val p = async { attempt { piggies.list() } }
                Triple(a.await(), m.await(), p.await())
            }
            _state.update {
                it.copy(
                    accounts = accounts.toLoad(),
                    firstName = me.getOrNull()?.let { c -> com.securebank.mobile.core.util.Format.firstName(c.name) } ?: it.firstName,
                    piggies = savings.getOrNull() ?: it.piggies,
                    refreshing = false,
                )
            }
        }
    }
}

package com.securebank.mobile.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsState(
    val accounts: Load<List<Account>> = Load.Loading,
    val newType: String = "CHECKING",
    val opening: Boolean = false,
    /** A conta recém-aberta (a tela mostra o resumo com agência, número e limites iniciais). */
    val opened: Account? = null,
    val openedLimits: Load<List<com.securebank.mobile.core.network.LimitUsage>> = Load.Loading,
    val openError: String? = null,
)

class AccountsViewModel(private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()
    private val intent = com.securebank.mobile.core.util.IdempotentIntent()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { load() } }
    }

    fun load() {
        _state.update { if (it.accounts is Load.Ready) it else it.copy(accounts = Load.Loading) }
        viewModelScope.launch {
            val result = attempt { banking.accounts() }
            _state.update { it.copy(accounts = result.toLoad()) }
        }
    }

    fun onType(type: String) = _state.update { it.copy(newType = type, openError = null) }

    fun openAnother() = _state.update { it.copy(opened = null, openError = null) }

    fun open() {
        val s = _state.value
        if (s.opening) return
        _state.update { it.copy(opening = true, openError = null) }
        viewModelScope.launch {
            val result = attempt { intent.run(s.newType) { key -> banking.openAccount(s.newType, key) } }
            result.onSuccess { account ->
                _state.update { it.copy(opening = false, opened = account, openedLimits = Load.Loading) }
                load()
                val limits = attempt { banking.limits(account.id) }
                _state.update { it.copy(openedLimits = limits.toLoad()) }
            }.onFailure { e ->
                _state.update { it.copy(opening = false, openError = messageFor(e)) }
            }
        }
    }
}

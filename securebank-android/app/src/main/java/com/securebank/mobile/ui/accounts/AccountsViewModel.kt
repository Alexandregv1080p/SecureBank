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
    val openedNumber: String? = null,
    val openError: String? = null,
)

class AccountsViewModel(private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { if (it.accounts is Load.Ready) it else it.copy(accounts = Load.Loading) }
        viewModelScope.launch {
            val result = attempt { banking.accounts() }
            _state.update { it.copy(accounts = result.toLoad()) }
        }
    }

    fun onType(type: String) = _state.update { it.copy(newType = type, openedNumber = null, openError = null) }

    fun open() {
        val s = _state.value
        if (s.opening) return
        _state.update { it.copy(opening = true, openError = null, openedNumber = null) }
        viewModelScope.launch {
            val result = attempt { banking.openAccount(s.newType) }
            result.onSuccess { account ->
                _state.update { it.copy(opening = false, openedNumber = account.accountNumber) }
                load()
            }.onFailure { e ->
                _state.update { it.copy(opening = false, openError = messageFor(e)) }
            }
        }
    }
}

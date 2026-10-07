package com.securebank.mobile.ui.fx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.FxOperation
import com.securebank.mobile.core.network.FxRate
import com.securebank.mobile.core.network.FxWallet
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.FxRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FxState(
    val rates: Load<List<FxRate>> = Load.Loading,
    val wallets: Load<List<FxWallet>> = Load.Loading,
    val operations: Load<List<FxOperation>> = Load.Loading,
    val refreshing: Boolean = false,
)

class FxViewModel(private val repository: FxRepository, banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(FxState())
    val state: StateFlow<FxState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { load() } }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val rates = attempt { repository.rates() }
            val wallets = attempt { repository.wallets() }
            val operations = attempt { repository.operations().items }
            _state.update { it.copy(rates = rates.toLoad(), wallets = wallets.toLoad(), operations = operations.toLoad(), refreshing = false) }
        }
    }
}

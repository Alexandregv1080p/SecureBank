package com.securebank.mobile.ui.piggy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.PiggyRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PiggiesState(val piggies: Load<List<Piggy>> = Load.Loading, val refreshing: Boolean = false)

class PiggiesViewModel(private val repository: PiggyRepository, banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PiggiesState())
    val state: StateFlow<PiggiesState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { load() } }
    }

    fun refresh() = load()

    private fun load() {
        _state.update { if (it.piggies is Load.Ready) it.copy(refreshing = true) else it.copy(piggies = Load.Loading) }
        viewModelScope.launch {
            val result = attempt { repository.list() }
            _state.update { it.copy(piggies = result.toLoad(), refreshing = false) }
        }
    }
}

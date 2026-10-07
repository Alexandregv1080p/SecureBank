package com.securebank.mobile.ui.invest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Investment
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.InvestmentRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvestmentDetailState(
    val investment: Load<Investment> = Load.Loading,
    val loading: Boolean = false,
    val error: String? = null,
)

class InvestmentDetailViewModel(
    private val repository: InvestmentRepository,
    banking: BankingRepository,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow(InvestmentDetailState())
    val state: StateFlow<InvestmentDetailState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { reload() } }
    }

    fun load() {
        _state.update { it.copy(investment = Load.Loading) }
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val result = attempt { repository.get(id) }.toLoad()
            _state.update { it.copy(investment = result) }
        }
    }

    /** Só chamar DEPOIS da confirmação de identidade. */
    fun redeem() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = intent.run(id) { key -> repository.redeem(id, key) }
                _state.update { it.copy(investment = Load.Ready(updated)) }
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

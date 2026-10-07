package com.securebank.mobile.ui.invest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Investment
import com.securebank.mobile.core.network.InvestmentProduct
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.InvestmentRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvestmentsState(
    val products: Load<List<InvestmentProduct>> = Load.Loading,
    val investments: Load<List<Investment>> = Load.Loading,
    val refreshing: Boolean = false,
) {
    /** Quanto vale hoje, líquido, tudo o que está aplicado. */
    val totalNet: String
        get() = ((investments as? Load.Ready)?.value.orEmpty().filter { it.active })
            .fold(BigDecimal.ZERO) { acc, i -> acc + (i.net.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
            .setScale(2).toPlainString()
}

class InvestmentsViewModel(private val repository: InvestmentRepository, banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(InvestmentsState())
    val state: StateFlow<InvestmentsState> = _state.asStateFlow()

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
            val products = attempt { repository.products() }
            val investments = attempt { repository.list() }
            _state.update { it.copy(products = products.toLoad(), investments = investments.toLoad(), refreshing = false) }
        }
    }
}

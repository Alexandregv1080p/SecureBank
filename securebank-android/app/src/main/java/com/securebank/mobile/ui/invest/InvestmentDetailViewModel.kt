package com.securebank.mobile.ui.invest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Investment
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.OperationValidation
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
    val amount: String = "",
    val amountError: String? = null,
    val message: String? = null,
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

    fun onAmount(v: String) = _state.update { it.copy(amount = com.securebank.mobile.core.util.Validation.sanitizeAmount(v), amountError = null, error = null) }

    /** Valida o valor do resgate parcial; a tela pede a biometria e só então chama [redeem]. */
    fun validatePartial(): Boolean {
        val error = OperationValidation.amount(_state.value.amount)
        _state.update { it.copy(amountError = error) }
        return error == null
    }

    /** Só chamar DEPOIS da confirmação de identidade. [partial]: resgata só o valor digitado; senão, tudo. */
    fun redeem(partial: Boolean) {
        val s = _state.value
        if (s.loading) return
        val amount = if (partial) Money.parse(s.amount) ?: return else null
        _state.update { it.copy(loading = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val updated = intent.run(Pair(id, amount)) { key -> repository.redeem(id, key, amount) }
                _state.update {
                    it.copy(
                        investment = Load.Ready(updated), amount = "",
                        message = updated.paidAmount?.let { p -> "Resgatado ${Money.format(p.amount)}. O valor já está na sua conta." },
                    )
                }
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

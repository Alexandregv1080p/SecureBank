package com.securebank.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.PixEntry
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.PixValidation
import com.securebank.mobile.data.PixRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PixRefundState(
    val original: Load<PixEntry> = Load.Loading,
    val amount: String = "",
    val amountError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: PixEntry? = null,
)

/** Devolver (parte de) um Pix recebido. Os dados vêm do servidor, que também confere prazo e valor restante. */
class PixRefundViewModel(private val pix: PixRepository, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(PixRefundState())
    val state: StateFlow<PixRefundState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    private data class Payload(val id: String, val amount: String)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val load = attempt { pix.transfer(id) }.toLoad()
            _state.update { s ->
                val entry = (load as? Load.Ready)?.value
                s.copy(original = load, amount = if (s.amount.isEmpty() && entry?.canRefund == true) entry.refundableAmount!!.amount.replace('.', ',') else s.amount)
            }
        }
    }

    fun onAmount(v: String) = _state.update { it.copy(amount = com.securebank.mobile.core.util.Validation.sanitizeAmount(v), amountError = null, error = null) }

    /** Só chamar DEPOIS da confirmação de identidade. */
    fun confirm() {
        val s = _state.value
        val entry = (s.original as? Load.Ready)?.value ?: return
        if (s.loading || !entry.canRefund) return
        val error = PixValidation.refundAmountError(s.amount, entry.refundableAmount!!.amount)
        if (error != null) {
            _state.update { it.copy(amountError = error) }
            return
        }
        val payload = Payload(id, Money.parse(s.amount)!!)
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val refund = intent.run(payload) { key -> pix.refund(payload.id, payload.amount, key) }
                _state.update { it.copy(done = refund) }
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

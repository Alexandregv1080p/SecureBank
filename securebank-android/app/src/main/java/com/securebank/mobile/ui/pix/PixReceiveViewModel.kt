package com.securebank.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.PixKey
import com.securebank.mobile.core.pix.BrCode
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.OperationValidation
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.PixRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PixReceiveState(
    val keys: Load<List<PixKey>> = Load.Loading,
    val name: String = "",
    val selectedKeyId: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    /** Pix Copia e Cola pronto (o mesmo texto do QR); null enquanto falta chave, nome ou o valor digitado é inválido. */
    val code: String? = null,
)

/** Receber: escolhe a chave e (opcional) o valor; o QR e o copia-e-cola são gerados aqui, no aparelho. */
class PixReceiveViewModel(private val pix: PixRepository, private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PixReceiveState())
    val state: StateFlow<PixReceiveState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val keys = attempt { pix.keys() }
            val me = attempt { banking.me() }
            _state.update { s ->
                val load = keys.toLoad()
                val first = (load as? Load.Ready)?.value?.firstOrNull()?.id
                rebuild(s.copy(keys = load, name = me.getOrNull()?.name ?: s.name, selectedKeyId = s.selectedKeyId ?: first))
            }
        }
    }

    fun onKey(id: String) = _state.update { rebuild(it.copy(selectedKeyId = id)) }

    fun onAmount(raw: String) = _state.update {
        rebuild(it.copy(amount = com.securebank.mobile.core.util.Validation.sanitizeAmount(raw)))
    }

    private fun rebuild(s: PixReceiveState): PixReceiveState {
        val key = (s.keys as? Load.Ready)?.value?.firstOrNull { it.id == s.selectedKeyId }
        val amount = if (s.amount.isBlank()) null else Money.parse(s.amount)
        val amountError = if (s.amount.isBlank() || amount != null) null else com.securebank.mobile.core.util.Validation.amountIssue(s.amount)
        val code = if (key == null || amountError != null) null else BrCode.encode(key.key, s.name.ifBlank { "Recebedor" }, amount = amount)
        return s.copy(amountError = amountError, code = code)
    }
}

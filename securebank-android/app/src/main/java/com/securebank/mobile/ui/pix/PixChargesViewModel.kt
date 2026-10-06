package com.securebank.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.PixCharge
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.pix.BrCode
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.OperationValidation
import com.securebank.mobile.core.util.PixValidation
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

/** Validade da cobrança, em minutos (o servidor aceita de 1 minuto a 7 dias). */
enum class ChargeValidity(val label: String, val minutes: Int) { HOUR("1 hora", 60), DAY("1 dia", 1440), WEEK("7 dias", 10080) }

data class PixChargesState(
    val accounts: Load<List<Account>> = Load.Loading,
    val sourceId: String? = null,
    val amount: String = "",
    val description: String = "",
    val validity: ChargeValidity = ChargeValidity.DAY,
    val amountError: String? = null,
    val descriptionError: String? = null,
    val charges: Load<List<PixCharge>> = Load.Loading,
    val creating: Boolean = false,
    val error: String? = null,
    /** Cobrança mostrada em QR (recém-criada ou escolhida na lista) e o copia-e-cola dela. */
    val shown: PixCharge? = null,
    val shownCode: String? = null,
    val receiverName: String = "",
)

/** Cobrar: cria a cobrança no servidor (valor fixo, validade, uso único) e mostra o QR dinâmico que aponta para ela. */
class PixChargesViewModel(private val pix: PixRepository, private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PixChargesState())
    val state: StateFlow<PixChargesState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { loadCharges() } }
    }

    fun load() {
        viewModelScope.launch {
            val accounts = attempt { banking.accounts() }
            val me = attempt { banking.me() }
            _state.update { s ->
                val load = accounts.toLoad()
                s.copy(
                    accounts = load, receiverName = me.getOrNull()?.name ?: s.receiverName,
                    sourceId = s.sourceId ?: (load as? Load.Ready)?.value?.firstOrNull()?.id,
                )
            }
        }
        loadCharges()
    }

    private fun loadCharges() {
        viewModelScope.launch {
            val result = attempt { pix.charges(0, 30).items }.toLoad()
            _state.update { it.copy(charges = result) }
        }
    }

    fun onSource(id: String) = _state.update { it.copy(sourceId = id) }
    fun onAmount(v: String) = _state.update { it.copy(amount = v.filter { c -> c.isDigit() || c == ',' || c == '.' }, amountError = null, error = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v.take(PixValidation.MESSAGE_MAX + 5), descriptionError = null) }
    fun onValidity(v: ChargeValidity) = _state.update { it.copy(validity = v) }

    fun create() {
        val s = _state.value
        if (s.creating) return
        val amount = Money.parse(s.amount)
        val amountError = if (amount == null) OperationValidation.AMOUNT_MESSAGE else null
        val descriptionError = if (s.description.trim().length > PixValidation.MESSAGE_MAX) "No máximo ${PixValidation.MESSAGE_MAX} caracteres" else null
        if (amountError != null || descriptionError != null || s.sourceId == null) {
            _state.update { it.copy(amountError = amountError, descriptionError = descriptionError, error = if (s.sourceId == null) "Escolha a conta que vai receber." else null) }
            return
        }
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            try {
                val charge = pix.createCharge(s.sourceId, amount!!, s.description.trim().ifEmpty { null }, s.validity.minutes)
                show(charge)
                _state.update { it.copy(amount = "", description = "") }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(creating = false) }
            }
        }
    }

    fun show(charge: PixCharge) = _state.update {
        it.copy(shown = charge, shownCode = BrCode.encodeDynamic(charge.location, it.receiverName.ifBlank { "Recebedor" }))
    }

    fun hide() = _state.update { it.copy(shown = null, shownCode = null) }

    fun cancel(charge: PixCharge) {
        viewModelScope.launch {
            try {
                pix.cancelCharge(charge.txid)
                _state.update { if (it.shown?.txid == charge.txid) it.copy(shown = null, shownCode = null) else it }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            }
        }
    }
}

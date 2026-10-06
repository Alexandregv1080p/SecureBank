package com.securebank.mobile.ui.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.PixEntry
import com.securebank.mobile.core.network.PixLookup
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.pix.BrCode
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.PixField
import com.securebank.mobile.core.util.PixValidation
import com.securebank.mobile.data.BankingRepository
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

enum class PixSendStep { Key, Details, Review, Sent }

/** Chave usada num Pix anterior, para reenviar com um toque. */
data class PixRecent(val key: String, val name: String)

data class PixSendState(
    val step: PixSendStep = PixSendStep.Key,
    val keyInput: String = "",
    val keyError: String? = null,
    val lookup: PixLookup? = null,
    /** Chave já resolvida (a digitada, ou a que veio dentro do copia-e-cola). */
    val resolvedKey: String = "",
    /** O código colado já trazia o valor: não dá para mudar. */
    val fixedAmount: Boolean = false,
    val accounts: Load<List<Account>> = Load.Loading,
    val sourceId: String? = null,
    val amount: String = "",
    val message: String = "",
    val errors: Map<PixField, String> = emptyMap(),
    val recents: List<PixRecent> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val receipt: PixEntry? = null,
)

/** Enviar Pix: chave (ou copia-e-cola) → consulta mascarada → valor → revisão → (biometria na tela) → comprovante. */
class PixSendViewModel(private val pix: PixRepository, private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PixSendState())
    val state: StateFlow<PixSendState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    private data class Payload(val source: String, val key: String, val amount: String, val message: String?)

    init {
        viewModelScope.launch {
            val accounts = attempt { banking.accounts() }
            _state.update { s ->
                val load = accounts.toLoad()
                s.copy(accounts = load, sourceId = s.sourceId ?: (load as? Load.Ready)?.value?.singleOrNull()?.id)
            }
        }
        viewModelScope.launch {
            val history = attempt { pix.history(0, 20) }
            history.getOrNull()?.let { page ->
                val recents = page.items.filter { it.sent }.distinctBy { it.key }.take(5).map { PixRecent(it.key, it.counterpartName) }
                _state.update { it.copy(recents = recents) }
            }
        }
    }

    fun onKeyInput(v: String) = _state.update { it.copy(keyInput = v, keyError = null, error = null) }
    fun onSource(id: String) = _state.update { it.copy(sourceId = id, errors = it.errors - PixField.Source) }
    fun onAmount(v: String) = _state.update { it.copy(amount = v.filter { c -> c.isDigit() || c == ',' || c == '.' }, errors = it.errors - PixField.Amount, error = null) }
    fun onMessage(v: String) = _state.update { it.copy(message = v.take(PixValidation.MESSAGE_MAX + 5), errors = it.errors - PixField.Message) }

    fun useRecent(recent: PixRecent) {
        _state.update { it.copy(keyInput = recent.key, keyError = null) }
        continueWithKey()
    }

    /** Resolve a chave (ou lê o código colado) e vai para o valor. */
    fun continueWithKey() {
        val s = _state.value
        if (s.loading) return
        PixValidation.keyError(s.keyInput)?.let { msg ->
            _state.update { it.copy(keyError = msg) }
            return
        }
        _state.update { it.copy(loading = true, keyError = null, error = null) }
        viewModelScope.launch {
            try {
                var key = s.keyInput.trim()
                var amount: String? = null
                if (BrCode.looksLikeCode(key)) {
                    val data = BrCode.decode(key)
                    key = data.key
                    amount = data.amount?.takeIf { Money.parse(it) != null }
                }
                val lookup = pix.lookup(key)
                _state.update {
                    it.copy(
                        step = PixSendStep.Details, lookup = lookup, resolvedKey = key,
                        fixedAmount = amount != null, amount = amount ?: it.amount,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BrCode.InvalidBrCodeException) {
                _state.update { it.copy(keyError = e.message) }
            } catch (e: ApiError) {
                _state.update {
                    if (e.status == 404) it.copy(keyError = "Chave Pix não encontrada. Confira e tente de novo.")
                    else it.copy(error = messageFor(e))
                }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun review() {
        val s = _state.value
        val errors = PixValidation.send(s.sourceId, s.amount, s.message)
        _state.update { if (errors.isEmpty()) it.copy(step = PixSendStep.Review, errors = emptyMap()) else it.copy(errors = errors) }
    }

    fun backToDetails() = _state.update { it.copy(step = PixSendStep.Details, error = null) }

    fun backToKey() = _state.update { it.copy(step = PixSendStep.Key, lookup = null, fixedAmount = false, error = null) }

    /** Só chamar DEPOIS da confirmação de identidade. */
    fun confirm() {
        val s = _state.value
        if (s.loading || s.step != PixSendStep.Review) return
        val amount = Money.parse(s.amount) ?: return
        val message = s.message.trim().ifEmpty { null }
        val payload = Payload(s.sourceId!!, s.resolvedKey, amount, message)
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val entry = intent.run(payload) { key -> pix.send(payload.source, payload.key, payload.amount, payload.message, key) }
                _state.update { it.copy(step = PixSendStep.Sent, receipt = entry, amount = "", message = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun newPix() = _state.value.let {
        _state.value = PixSendState(accounts = it.accounts, sourceId = it.sourceId, recents = it.recents)
    }
}

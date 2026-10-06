package com.securebank.mobile.ui.piggy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.OperationValidation
import com.securebank.mobile.core.util.PiggyValidation
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.PiggyRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PiggyAction { SAVE, REDEEM }

data class PiggyDetailState(
    val piggy: Load<Piggy> = Load.Loading,
    val amount: String = "",
    val amountError: String? = null,
    val editName: String = "",
    val editGoal: String = "",
    val editError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: String? = null,
    /** Porquinho fechado: a tela volta para a lista. */
    val closed: Boolean = false,
)

class PiggyDetailViewModel(
    private val repository: PiggyRepository,
    banking: BankingRepository,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PiggyDetailState())
    val state: StateFlow<PiggyDetailState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    private data class Payload(val id: String, val action: PiggyAction, val value: String)

    init {
        load()
        viewModelScope.launch { banking.changes.collect { reload() } }
    }

    fun load() {
        _state.update { it.copy(piggy = Load.Loading) }
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val result = attempt { repository.get(id) }
            _state.update { s ->
                val piggy = result.getOrNull()
                s.copy(
                    piggy = result.toLoad(),
                    // os campos de edição acompanham o servidor enquanto o usuário não os está mudando
                    editName = if (piggy != null && s.editName.isEmpty()) piggy.name else s.editName,
                    editGoal = if (piggy != null && s.editGoal.isEmpty()) goalText(piggy) else s.editGoal,
                )
            }
        }
    }

    private fun goalText(p: Piggy) = p.goal?.amount?.replace('.', ',').orEmpty()

    fun onAmount(raw: String) = _state.update {
        it.copy(amount = raw.filter { c -> c.isDigit() || c == ',' || c == '.' }, amountError = null, error = null, done = null)
    }

    fun onEditName(v: String) = _state.update { it.copy(editName = v.take(PiggyValidation.NAME_MAX + 5), editError = null, done = null) }
    fun onEditGoal(v: String) = _state.update { it.copy(editGoal = v.filter { c -> c.isDigit() || c == ',' || c == '.' }, editError = null, done = null) }

    fun submit(action: PiggyAction) {
        val s = _state.value
        if (s.loading) return
        val value = Money.parse(s.amount)
        if (value == null) {
            _state.update { it.copy(amountError = OperationValidation.AMOUNT_MESSAGE) }
            return
        }
        run {
            intent.run(Payload(id, action, value)) { key ->
                if (action == PiggyAction.SAVE) repository.save(id, value, key) else repository.redeem(id, value, key)
            }
            val verb = if (action == PiggyAction.SAVE) "guardado" else "resgatado"
            _state.update { it.copy(amount = "", done = "${Money.format(value)} $verb.") }
        }
    }

    /** Salva nome e/ou meta (meta em branco remove a meta). */
    fun saveEdits() {
        val s = _state.value
        val current = (s.piggy as? Load.Ready)?.value ?: return
        if (s.loading) return
        if (s.editName.isBlank() || s.editName.trim().length > PiggyValidation.NAME_MAX) {
            _state.update { it.copy(editError = "O nome tem de 1 a ${PiggyValidation.NAME_MAX} caracteres") }
            return
        }
        if (s.editGoal.isNotBlank() && Money.parse(s.editGoal) == null) {
            _state.update { it.copy(editError = OperationValidation.AMOUNT_MESSAGE) }
            return
        }
        run {
            if (s.editName.trim() != current.name) repository.rename(id, s.editName)
            val goal = PiggyValidation.goalOrNull(s.editGoal)
            if (goal == null && current.goal != null) repository.clearGoal(id)
            else if (goal != null && goal != current.goal?.amount) repository.setGoal(id, goal)
            _state.update { it.copy(done = "Alterações salvas.") }
        }
    }

    fun close() {
        if (_state.value.loading) return
        run {
            repository.close(id)
            _state.update { it.copy(closed = true) }
        }
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null, done = null) }
        viewModelScope.launch {
            try {
                block()
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

package com.securebank.mobile.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.MfaSetup
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.data.SecurityRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MfaState(
    val enabled: Load<Boolean> = Load.Loading,
    /** Segredo recém-gerado (só existe entre "Ativar" e a confirmação; nunca é salvo nem registrado em log). */
    val setup: MfaSetup? = null,
    val code: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class MfaViewModel(private val security: SecurityRepository) : ViewModel() {
    private val _state = MutableStateFlow(MfaState())
    val state: StateFlow<MfaState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val status = attempt { security.mfaEnabled() }
            _state.update { it.copy(enabled = status.toLoad()) }
        }
    }

    fun start() = setup { security.setupMfa() }

    fun cancel() = _state.update { it.copy(setup = null, code = "", error = null) }

    fun onCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        if (s.code.length != 6) {
            _state.update { it.copy(error = "Digite os 6 dígitos do aplicativo") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (s.setup != null) security.confirmMfa(s.code) else security.disableMfa(s.code)
                _state.update { it.copy(setup = null, code = "") } // o segredo some da memória da tela
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e), code = "") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun setup(call: suspend () -> MfaSetup) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = attempt { call() }
            _state.update { s ->
                result.fold(
                    onSuccess = { s.copy(setup = it, loading = false) },
                    onFailure = { e -> s.copy(error = messageFor(e), loading = false) },
                )
            }
        }
    }
}

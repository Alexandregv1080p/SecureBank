package com.securebank.mobile.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.PasswordField
import com.securebank.mobile.core.util.PasswordValidation
import com.securebank.mobile.data.SecurityRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PasswordState(
    val current: String = "",
    val next: String = "",
    val confirm: String = "",
    val errors: Map<PasswordField, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

class PasswordViewModel(private val security: SecurityRepository) : ViewModel() {
    private val _state = MutableStateFlow(PasswordState())
    val state: StateFlow<PasswordState> = _state.asStateFlow()

    fun onChange(field: PasswordField, value: String) = _state.update {
        val cleared = it.errors - field
        when (field) {
            PasswordField.Current -> it.copy(current = value, errors = cleared, error = null, done = false)
            PasswordField.Next -> it.copy(next = value, errors = cleared, error = null, done = false)
            PasswordField.Confirm -> it.copy(confirm = value, errors = cleared, error = null, done = false)
        }
    }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val errors = PasswordValidation.validate(s.current, s.next, s.confirm)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(loading = true, error = null, done = false) }
        viewModelScope.launch {
            try {
                security.changePassword(s.current, s.next)
                _state.update { it.copy(current = "", next = "", confirm = "", done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // a senha atual errada limpa só ela; as senhas não ficam na tela depois do envio
                _state.update { it.copy(error = messageFor(e), current = "", next = "", confirm = "") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

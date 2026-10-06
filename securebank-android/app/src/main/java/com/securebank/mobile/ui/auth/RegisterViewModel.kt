package com.securebank.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.Phone
import com.securebank.mobile.core.util.RegisterField
import com.securebank.mobile.core.util.RegisterValidation
import com.securebank.mobile.data.AuthRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterState(
    val name: String = "",
    val document: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirm: String = "",
    val errors: Map<RegisterField, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun onChange(field: RegisterField, value: String) = _state.update {
        val cleared = it.errors - field
        when (field) {
            RegisterField.Name -> it.copy(name = value, errors = cleared, error = null)
            RegisterField.Document -> it.copy(document = value.filter { c -> c.isDigit() }.take(11), errors = cleared, error = null)
            RegisterField.Email -> it.copy(email = value, errors = cleared, error = null)
            RegisterField.Phone -> it.copy(phone = value, errors = cleared, error = null)
            RegisterField.Password -> it.copy(password = value, errors = cleared, error = null)
            RegisterField.Confirm -> it.copy(confirm = value, errors = cleared, error = null)
        }
    }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val errors = RegisterValidation.validate(s.name, s.document, s.email, s.phone, s.password, s.confirm)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                auth.register(s.name, s.document, s.email, Phone.toE164BR(s.phone)!!, s.password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e), password = "", confirm = "") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

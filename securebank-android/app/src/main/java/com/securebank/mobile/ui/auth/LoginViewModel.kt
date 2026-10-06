package com.securebank.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.data.AuthRepository
import com.securebank.mobile.data.LoginOutcome
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val code: String = "",
    /** Não nulo = etapa do código de verificação em duas etapas. */
    val mfaToken: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, emailError = null, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val emailError = if (s.email.isBlank()) "Informe o e-mail" else null
        val passwordError = if (s.password.isEmpty()) "Informe a senha" else null
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        launchAction {
            when (val outcome = auth.login(s.email, s.password)) {
                LoginOutcome.Done -> Unit // a sessão mudou: a raiz troca de tela
                is LoginOutcome.MfaRequired -> _state.update { it.copy(mfaToken = outcome.mfaToken) }
            }
            // a senha não fica na memória da tela depois de enviada
            _state.update { it.copy(password = "") }
        }
    }

    fun onCode(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) submitCode()
    }

    fun submitCode() {
        val s = _state.value
        val token = s.mfaToken ?: return
        if (s.loading || s.code.length != 6) return
        launchAction {
            auth.verifyMfa(token, s.code)
        }
    }

    fun cancelMfa() = _state.update { it.copy(mfaToken = null, code = "", error = null) }

    private fun launchAction(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // código errado ou desafio expirado: limpa o código e a senha para uma nova tentativa limpa
                _state.update { it.copy(error = messageFor(e), code = "", password = "") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

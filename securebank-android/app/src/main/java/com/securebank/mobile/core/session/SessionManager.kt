package com.securebank.mobile.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    /** App abrindo: ainda tentando recuperar a sessão com o refresh token guardado. */
    data object Restoring : SessionState

    /** [expired]: a sessão existia e foi perdida (refresh recusado/reusado); a tela de login avisa. */
    data class SignedOut(val expired: Boolean = false) : SessionState

    data class SignedIn(val claims: Claims) : SessionState
}

/**
 * O access token (15 min) vive SÓ aqui, em memória: fechar o app o descarta e nada dele chega ao disco.
 * Quem sobrevive é o refresh token, cifrado pelo Keystore (ver [SecureTokenStore]).
 */
class SessionManager {
    @Volatile
    var accessToken: String? = null
        private set

    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun start(token: String) {
        val claims = Claims.decode(token)
        accessToken = token
        _state.value = SessionState.SignedIn(claims)
    }

    /** Sessão perdida contra a vontade do usuário (refresh recusado). */
    fun expire() {
        accessToken = null
        _state.value = SessionState.SignedOut(expired = true)
    }

    fun signOut() {
        accessToken = null
        _state.value = SessionState.SignedOut()
    }

    /** Fim da restauração sem sucesso e sem culpa do usuário (ex.: sem rede): vai para o login sem aviso de expiração. */
    fun restoreFailed() {
        if (_state.value is SessionState.Restoring) signOut()
    }
}

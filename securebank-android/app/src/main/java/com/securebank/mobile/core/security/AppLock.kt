package com.securebank.mobile.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bloqueio do app por biometria/credencial do aparelho. Bloqueia ao abrir (sessão restaurada do disco) e ao voltar de
 * segundo plano depois de [timeoutMs]. A carência curta evita pedir a digital só por trocar para o app autenticador
 * para ler o código. É um portão de INTERFACE: a proteção do token em si é o Keystore (ver SecureTokenStore).
 *
 * @param now relógio monotônico em ms (SystemClock.elapsedRealtime no app, controlável nos testes)
 * @param enabled preferência do usuário E aparelho com biometria/credencial configurada
 */
class AppLock(
    private val timeoutMs: Long,
    private val now: () -> Long,
    private val enabled: () -> Boolean,
) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun onBackgrounded() {
        backgroundedAt = now()
    }

    fun onForegrounded() {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        if (enabled() && now() - at >= timeoutMs) _locked.value = true
    }

    /** O bloqueio está valendo (preferência ligada e aparelho com biometria/credencial)? */
    fun isEnabled(): Boolean = enabled()

    /** Abertura do app com sessão restaurada: pede a identidade antes de mostrar qualquer saldo. */
    fun lockNow() {
        if (enabled()) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    /** Saiu da sessão: não há o que bloquear. */
    fun reset() {
        _locked.value = false
        backgroundedAt = null
    }
}

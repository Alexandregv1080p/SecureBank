package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.AuthApi
import com.securebank.mobile.core.network.LoginRequest
import com.securebank.mobile.core.network.MfaVerifyRequest
import com.securebank.mobile.core.network.RegisterRequest
import com.securebank.mobile.core.network.TokenResponse
import com.securebank.mobile.core.network.apiCall
import com.securebank.mobile.core.session.Claims
import com.securebank.mobile.core.session.SecureTokenStore
import com.securebank.mobile.core.session.SessionManager
import kotlinx.serialization.json.Json

sealed interface LoginOutcome {
    /** Sessão iniciada (o [SessionManager] já está SignedIn). */
    data object Done : LoginOutcome

    /** A conta tem verificação em duas etapas: falta o código TOTP. */
    data class MfaRequired(val mfaToken: String) : LoginOutcome
}

class AuthRepository(
    private val auth: AuthApi,
    private val store: SecureTokenStore,
    private val session: SessionManager,
    private val json: Json,
) {
    suspend fun login(email: String, password: String): LoginOutcome =
        accept(apiCall(json) { auth.login(LoginRequest(email.trim(), password)) })

    suspend fun verifyMfa(mfaToken: String, code: String): LoginOutcome =
        accept(apiCall(json) { auth.verifyMfa(MfaVerifyRequest(mfaToken, code)) })

    /** Cria o cadastro e já entra (como o web). O telefone chega normalizado em E.164. */
    suspend fun register(name: String, document: String, email: String, phone: String, password: String): LoginOutcome {
        apiCall(json) { auth.register(RegisterRequest(name.trim(), document, email.trim(), phone, password)) }
        return login(email, password)
    }

    /** Sai de verdade: revoga a sessão no servidor (se der) e apaga o refresh token do aparelho de qualquer jeito. */
    suspend fun logout() {
        try {
            apiCall(json) { auth.logout() }
        } catch (_: ApiError) {
            // sem rede ou token já inválido: a sessão local some mesmo assim; a do servidor expira sozinha
        }
        store.clear()
        session.signOut()
    }

    private suspend fun accept(response: TokenResponse): LoginOutcome {
        if (response.mfaRequired && response.mfaToken != null) return LoginOutcome.MfaRequired(response.mfaToken)

        val access = response.accessToken
        val refresh = response.refreshToken
        if (access == null || refresh == null) throw ApiError(0, "BAD_RESPONSE", "Resposta inesperada do servidor")
        val claims = try {
            Claims.decode(access)
        } catch (e: IllegalArgumentException) {
            throw ApiError(0, "BAD_RESPONSE", "Resposta inesperada do servidor")
        }

        // Contas da equipe (sem cliente) não usam o app: encerra a sessão que o servidor acabou de abrir.
        if (!claims.isCustomer) {
            session.start(access)
            logout()
            throw ApiError(403, "NOT_A_CUSTOMER", "Este aplicativo é para clientes")
        }

        store.set(refresh) // o refresh vai para o Keystore ANTES de a sessão existir
        session.start(access)
        return LoginOutcome.Done
    }
}

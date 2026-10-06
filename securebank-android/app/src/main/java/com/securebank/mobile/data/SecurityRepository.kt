package com.securebank.mobile.data

import com.securebank.mobile.core.network.ChangePasswordRequest
import com.securebank.mobile.core.network.CodeRequest
import com.securebank.mobile.core.network.MfaSetup
import com.securebank.mobile.core.network.SecurityApi
import com.securebank.mobile.core.network.SessionInfo
import com.securebank.mobile.core.network.apiCall
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

/** MFA, senha e dispositivos conectados. [changes] avisa quando algo de segurança mudou (a lista de sessões recarrega). */
class SecurityRepository(private val api: SecurityApi, private val json: Json) {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun mfaEnabled(): Boolean = apiCall(json) { api.mfaStatus().enabled }

    suspend fun sessions(): List<SessionInfo> = apiCall(json) { api.sessions() }

    suspend fun revokeSession(id: String) {
        apiCall(json) { api.revokeSession(id) }
        _changes.tryEmit(Unit)
    }

    /** O servidor encerra as outras sessões ao trocar a senha. */
    suspend fun changePassword(current: String, new: String) {
        apiCall(json) { api.changePassword(ChangePasswordRequest(current, new)) }
        _changes.tryEmit(Unit)
    }

    /** Devolve o segredo UMA vez (o servidor não o mostra de novo): quem chama não deve guardá-lo nem registrá-lo. */
    suspend fun setupMfa(): MfaSetup = apiCall(json) { api.setupMfa() }

    suspend fun confirmMfa(code: String) {
        apiCall(json) { api.confirmMfa(CodeRequest(code)) }
        _changes.tryEmit(Unit)
    }

    suspend fun disableMfa(code: String) {
        apiCall(json) { api.disableMfa(CodeRequest(code)) }
        _changes.tryEmit(Unit)
    }
}

package com.securebank.mobile.core.network

import com.securebank.mobile.core.session.SecureTokenStore
import com.securebank.mobile.core.session.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Renova o access token com o refresh token. UMA renovação por vez: se várias requisições levam 401 juntas, a primeira
 * renova e as outras só reaproveitam o resultado. Isso importa porque o refresh é **rotativo**: usar o mesmo token duas
 * vezes seria lido pelo servidor como roubo e derrubaria a sessão inteira.
 */
class TokenRefresher(
    private val auth: AuthApi,
    private val store: SecureTokenStore,
    private val session: SessionManager,
    private val json: Json,
) {
    private val mutex = Mutex()

    /**
     * @param staleToken o access token que acabou de ser recusado (null na abertura do app).
     * @return true se há um access token utilizável ao final.
     */
    suspend fun refresh(staleToken: String?): Boolean = mutex.withLock {
        val current = session.accessToken
        if (current != null && current != staleToken) return true // outro chamador já renovou enquanto esperávamos

        val refreshToken = store.get()
        if (refreshToken == null) {
            session.expire()
            return false
        }
        try {
            val response = apiCall(json) { auth.refresh(RefreshRequest(refreshToken)) }
            val access = response.accessToken
            val rotated = response.refreshToken
            if (access == null || rotated == null) {
                store.clear()
                session.expire()
                return false
            }
            store.set(rotated) // grava o novo ANTES de usar: se o app morrer agora, o token antigo já não serve
            session.start(access)
            true
        } catch (e: ApiError) {
            if (e.uncertain || e.unavailable) {
                // Sem rede, servidor ou Redis fora: a sessão pode estar perfeita. Mantém tudo e deixa tentar depois.
                false
            } else {
                // 401 (refresh inválido, expirado ou REUSADO): a sessão acabou de verdade.
                store.clear()
                session.expire()
                false
            }
        }
    }
}

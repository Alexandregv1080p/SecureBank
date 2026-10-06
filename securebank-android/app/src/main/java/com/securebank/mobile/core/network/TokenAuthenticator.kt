package com.securebank.mobile.core.network

import com.securebank.mobile.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Em 401 de uma rota autenticada: renova a sessão UMA vez e repete a requisição com o token novo. */
class TokenAuthenticator(
    private val session: SessionManager,
    private val refresher: () -> TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val sent = response.request.header("Authorization") ?: return null // rota sem token: nada a renovar
        if (priorResponses(response) >= 1) return null // já repetimos uma vez e ainda deu 401

        val stale = sent.removePrefix("Bearer ")
        val renewed = runBlocking { refresher().refresh(stale) }
        val token = session.accessToken
        return if (renewed && token != null) {
            response.request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            null
        }
    }

    private fun priorResponses(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

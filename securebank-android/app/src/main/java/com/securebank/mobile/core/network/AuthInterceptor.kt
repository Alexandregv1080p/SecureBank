package com.securebank.mobile.core.network

import com.securebank.mobile.core.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/** Põe o access token (da memória) e o User-Agent (aparece na lista de dispositivos); rotas "No-Auth" seguem sem token. */
class AuthInterceptor(private val session: SessionManager, private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header("User-Agent", userAgent)
        if (request.header(NO_AUTH) != null) {
            builder.removeHeader(NO_AUTH)
        } else {
            session.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        const val NO_AUTH = "No-Auth"
    }
}

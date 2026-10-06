package com.securebank.mobile.core.network

import com.securebank.mobile.core.session.SecureTokenStore
import com.securebank.mobile.core.session.SessionManager
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.CertificatePinner
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class Network(
    val json: Json,
    val auth: AuthApi,
    val banking: BankingApi,
    val security: SecurityApi,
    val refresher: TokenRefresher,
)

object NetworkFactory {
    val json = Json {
        ignoreUnknownKeys = true // a API pode ganhar campos sem quebrar versões antigas do app
        explicitNulls = false // não manda "description": null
    }

    /**
     * @param certPins pins SHA-256 do certificado (formato "sha256/...", separados por vírgula). Vazio = sem pinning
     * (só debug). Nunca há log de corpo de requisição ou resposta: ali trafegam senha e tokens.
     */
    fun create(
        baseUrl: String,
        store: SecureTokenStore,
        session: SessionManager,
        userAgent: String,
        certPins: String = "",
    ): Network {
        lateinit var refresher: TokenRefresher

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session, userAgent))
            .authenticator(TokenAuthenticator(session) { refresher })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .apply { pinner(baseUrl, certPins)?.let { certificatePinner(it) } }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()

        val auth = retrofit.create(AuthApi::class.java)

        // O refresh roda DENTRO do Authenticator, que bloqueia uma thread do dispatcher do OkHttp (máx. 5 por host).
        // Se a chamada de refresh usasse o mesmo dispatcher, 5 requisições em 401 ao mesmo tempo ocuparam todas as
        // vagas esperando um refresh que nunca consegue começar (deadlock). Dispatcher próprio resolve; o pool de
        // conexões e os interceptors continuam compartilhados.
        val refreshClient = client.newBuilder().dispatcher(Dispatcher()).authenticator(Authenticator.NONE).build()
        val refreshAuth = retrofit.newBuilder().client(refreshClient).build().create(AuthApi::class.java)
        refresher = TokenRefresher(refreshAuth, store, session, json)
        return Network(
            json = json,
            auth = auth,
            banking = retrofit.create(BankingApi::class.java),
            security = retrofit.create(SecurityApi::class.java),
            refresher = refresher,
        )
    }

    private fun pinner(baseUrl: String, certPins: String): CertificatePinner? {
        val pins = certPins.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (pins.isEmpty()) return null
        val host = baseUrl.toHttpUrl().host
        return CertificatePinner.Builder().apply { pins.forEach { add(host, it) } }.build()
    }
}

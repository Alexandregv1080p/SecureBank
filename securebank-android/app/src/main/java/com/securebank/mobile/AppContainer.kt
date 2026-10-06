package com.securebank.mobile

import android.content.Context
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.session.KeystoreTokenStore
import com.securebank.mobile.core.session.SecureTokenStore
import com.securebank.mobile.core.session.SessionManager

/** Injeção de dependência manual: poucos objetos, todos criados uma vez, sem framework. */
class AppContainer(context: Context) {
    val session = SessionManager()
    val tokenStore: SecureTokenStore = KeystoreTokenStore(context)
    val network = NetworkFactory.create(
        baseUrl = BuildConfig.API_BASE_URL,
        store = tokenStore,
        session = session,
        userAgent = "SecureBank-Android/${BuildConfig.VERSION_NAME}",
        certPins = BuildConfig.CERT_PINS,
    )

    /** Na abertura: recupera a sessão com o refresh token guardado; sem ele (ou sem rede) cai no login. */
    suspend fun restoreSession() {
        if (tokenStore.get() == null) {
            session.signOut()
            return
        }
        if (!network.refresher.refresh(staleToken = null)) session.restoreFailed()
    }
}

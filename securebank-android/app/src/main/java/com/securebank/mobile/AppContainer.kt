package com.securebank.mobile

import android.content.Context
import android.os.SystemClock
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.security.AppLock
import com.securebank.mobile.core.security.BiometricGate
import com.securebank.mobile.core.security.LockSettings
import com.securebank.mobile.core.session.KeystoreTokenStore
import com.securebank.mobile.core.session.SecureTokenStore
import com.securebank.mobile.core.session.SessionManager
import com.securebank.mobile.data.AuthRepository
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.FxRepository
import com.securebank.mobile.data.InvestmentRepository
import com.securebank.mobile.data.PiggyRepository
import com.securebank.mobile.data.PixRepository
import com.securebank.mobile.data.SecurityRepository

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
    val auth = AuthRepository(network.auth, tokenStore, session, network.json)
    val banking = BankingRepository(network.banking, network.json)
    val security = SecurityRepository(network.security, network.json)
    val piggies = PiggyRepository(network.piggy, network.json, banking)
    val investments = InvestmentRepository(network.investment, network.json, banking)
    val fx = FxRepository(network.fx, network.json, banking)
    val pix = PixRepository(network.pix, network.json, banking)

    val lockSettings = LockSettings(context)
    val biometric = BiometricGate(context)
    val appLock = AppLock(
        timeoutMs = 30_000,
        now = SystemClock::elapsedRealtime,
        enabled = { lockSettings.enabled && biometric.isAvailable() },
    )

    /** Na abertura: recupera a sessão com o refresh token guardado; sem ele (ou sem rede) cai no login. */
    suspend fun restoreSession() {
        if (tokenStore.get() == null) {
            session.signOut()
            return
        }
        if (network.refresher.refresh(staleToken = null)) {
            appLock.lockNow() // sessão veio do disco: confirma a identidade antes de mostrar o saldo
        } else {
            session.restoreFailed()
        }
    }
}

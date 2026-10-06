package com.securebank.mobile.core.network

import com.securebank.mobile.core.session.SecureTokenStore
import com.securebank.mobile.core.session.SessionManager
import com.securebank.mobile.core.session.SessionState
import com.securebank.mobile.core.session.fakeJwt
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeTokenStore(var token: String? = null) : SecureTokenStore {
    override fun get() = token
    override fun set(token: String) { this.token = token }
    override fun clear() { token = null }
}

class NetworkTest {
    private val server = MockWebServer()
    private val oldAccess = fakeJwt(sid = "old")
    private val newAccess = fakeJwt(sid = "new")
    private lateinit var session: SessionManager
    private lateinit var store: FakeTokenStore
    private lateinit var network: Network
    private val refreshCalls = AtomicInteger()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun tokens(access: String, refresh: String) = json("""{"mfaRequired":false,"accessToken":"$access","refreshToken":"$refresh"}""")

    private fun error(status: Int, code: String) =
        json("""{"status":$status,"code":"$code","message":"msg","traceId":"trace-1"}""").setResponseCode(status)

    @Before
    fun setUp() {
        server.start()
        session = SessionManager()
        store = FakeTokenStore("rt-1")
        session.start(oldAccess)
        network = NetworkFactory.create(server.url("/api/v1/").toString(), store, session, "SecureBank-Android/test")
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() } // um teste já o derruba por conta própria
    }

    /** Servidor onde só o access token novo é aceito e o refresh responde conforme [refresh]. */
    private fun serve(refresh: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/refresh" -> { refreshCalls.incrementAndGet(); Thread.sleep(80); refresh() }
                request.getHeader("Authorization") == "Bearer $newAccess" -> json("[]")
                else -> error(401, "UNAUTHENTICATED")
            }
        }
    }

    @Test
    fun errorsBecomeApiErrorWithCodeAndTraceId() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = error(422, "INSUFFICIENT_FUNDS")
        }
        val e = runCatching { apiCall(network.json) { network.banking.account("x") } }.exceptionOrNull() as ApiError
        assertEquals(422, e.status)
        assertEquals("INSUFFICIENT_FUNDS", e.code)
        assertEquals("trace-1", e.traceId)
        assertFalse(e.uncertain)
        assertEquals("Saldo insuficiente.", messageFor(e))
    }

    @Test
    fun unavailableServiceIsUncertainAndCarriesRetryAfter() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                error(503, "SERVICE_UNAVAILABLE").setHeader("Retry-After", "5")
        }
        val e = runCatching { apiCall(network.json) { network.banking.accounts() } }.exceptionOrNull() as ApiError
        assertTrue(e.unavailable)
        assertTrue(e.uncertain)
        assertEquals(5, e.retryAfterSeconds)
    }

    @Test
    fun connectionFailureIsANetworkError() = runBlocking {
        server.shutdown()
        val e = runCatching { apiCall(network.json) { network.banking.accounts() } }.exceptionOrNull() as ApiError
        assertTrue(e.network)
        assertTrue(e.uncertain)
        assertEquals("Sem conexão com o servidor. Tente de novo.", messageFor(e))
    }

    @Test
    fun authRoutesNeverCarryTheAccessToken() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = tokens(newAccess, "rt-2")
        }
        apiCall(network.json) { network.auth.login(LoginRequest("a@b.c", "x")) }
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("No-Auth"))
        assertEquals("SecureBank-Android/test", recorded.getHeader("User-Agent"))
    }

    @Test
    fun a401RenewsTheSessionOnceAndRetriesWithTheNewToken() = runBlocking {
        serve { tokens(newAccess, "rt-2") }

        val accounts = network.banking.accounts()

        assertTrue(accounts.isEmpty())
        assertEquals(1, refreshCalls.get())
        assertEquals("rt-2", store.token) // refresh rotativo: o novo substituiu o antigo
        assertEquals(newAccess, session.accessToken)
        assertTrue(session.state.value is SessionState.SignedIn)
    }

    @Test
    fun parallel401sShareASingleRefresh() = runBlocking {
        serve { tokens(newAccess, "rt-2") }

        (1..5).map { async(Dispatchers.IO) { network.banking.accounts() } }.awaitAll()

        // com o refresh rotativo, duas renovações com o mesmo token seriam lidas como roubo
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun aRejectedRefreshEndsTheSessionAndForgetsTheToken() = runBlocking {
        serve { error(401, "INVALID_REFRESH_TOKEN") }

        val e = runCatching { network.banking.accounts() }.exceptionOrNull()

        assertTrue(e is retrofit2.HttpException)
        assertNull(store.token)
        assertNull(session.accessToken)
        assertEquals(SessionState.SignedOut(expired = true), session.state.value)
    }

    @Test
    fun aRefreshThatFailsBecauseOfTheServerKeepsTheSession() = runBlocking {
        serve { error(503, "SERVICE_UNAVAILABLE") }

        runCatching { network.banking.accounts() }

        assertEquals("rt-1", store.token) // nada de deslogar por causa de uma falha passageira do servidor
        assertTrue(session.state.value is SessionState.SignedIn)
    }

    @Test
    fun restoreFailureWithoutBlameGoesToLoginWithoutAnExpiryNotice() {
        val fresh = SessionManager()
        fresh.restoreFailed()
        assertEquals(SessionState.SignedOut(expired = false), fresh.state.value)
    }
}

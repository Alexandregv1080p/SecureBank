package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.FakeTokenStore
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.session.SessionManager
import com.securebank.mobile.core.session.SessionState
import com.securebank.mobile.core.session.fakeJwt
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private val server = MockWebServer()
    private lateinit var session: SessionManager
    private lateinit var store: FakeTokenStore
    private lateinit var repo: AuthRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun tokens(access: String, refresh: String) =
        json("""{"mfaRequired":false,"accessToken":"$access","refreshToken":"$refresh"}""")

    private fun route(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = handler(request)
        }
    }

    @Before
    fun setUp() {
        server.start()
        session = SessionManager()
        store = FakeTokenStore()
        val network = NetworkFactory.create(server.url("/api/v1/").toString(), store, session, "SecureBank-Android/test")
        repo = AuthRepository(network.auth, store, session, network.json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginKeepsTheRefreshTokenInTheStoreAndStartsTheSession() = runBlocking {
        route { tokens(fakeJwt(), "rt-9") }

        val outcome = repo.login("  ana@example.com ", "Correct-Horse-Battery-9")

        assertEquals(LoginOutcome.Done, outcome)
        assertEquals("rt-9", store.token)
        assertTrue(session.state.value is SessionState.SignedIn)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"email\":\"ana@example.com\"")) // e-mail aparado
    }

    @Test
    fun anMfaAccountStopsAtTheChallengeWithoutStoringAnything() = runBlocking {
        route { json("""{"mfaRequired":true,"mfaToken":"mfa-1"}""") }

        val outcome = repo.login("ana@example.com", "x")

        assertEquals(LoginOutcome.MfaRequired("mfa-1"), outcome)
        assertNull(store.token)
        assertEquals(SessionState.Restoring, session.state.value)
    }

    @Test
    fun theMfaCodeCompletesTheLogin() = runBlocking {
        route { tokens(fakeJwt(), "rt-2") }

        assertEquals(LoginOutcome.Done, repo.verifyMfa("mfa-1", "123456"))

        assertEquals("rt-2", store.token)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"mfaToken\":\"mfa-1\""))
    }

    @Test
    fun wrongCredentialsSurfaceAsAFriendlyApiError() {
        route { json("""{"status":401,"code":"INVALID_CREDENTIALS","message":"x","traceId":"t"}""", 401) }

        val e = runCatching { runBlocking { repo.login("a@b.c", "wrong") } }.exceptionOrNull() as ApiError

        assertEquals("INVALID_CREDENTIALS", e.code)
        assertEquals("E-mail ou senha incorretos.", messageFor(e))
        assertNull(store.token)
    }

    @Test
    fun staffAccountsAreRefusedAndTheirServerSessionIsClosed() {
        val paths = mutableListOf<String>()
        route { request ->
            paths += request.path!!
            if (request.path!!.endsWith("/auth/login")) tokens(fakeJwt(cid = null), "rt-staff") else MockResponse().setResponseCode(204)
        }

        val e = runCatching { runBlocking { repo.login("staff@securebank.local", "x") } }.exceptionOrNull() as ApiError

        assertEquals("NOT_A_CUSTOMER", e.code)
        assertNull(store.token)
        assertEquals(SessionState.SignedOut(expired = false), session.state.value)
        assertTrue(paths.any { it.endsWith("/auth/logout") }) // não deixa sessão aberta no servidor
    }

    @Test
    fun logoutForgetsEverythingLocallyEvenWhenTheServerFails() = runBlocking {
        route { json("""{"status":500,"code":"INTERNAL_ERROR","message":"x"}""", 500) }
        session.start(fakeJwt())
        store.token = "rt-1"

        repo.logout()

        assertNull(store.token)
        assertNull(session.accessToken)
        assertEquals(SessionState.SignedOut(expired = false), session.state.value)
    }

    @Test
    fun registerCreatesTheAccountThenSignsIn() = runBlocking {
        route { request ->
            if (request.path!!.endsWith("/auth/register")) json("""{"userId":"u","customerId":"c"}""", 201) else tokens(fakeJwt(), "rt-3")
        }

        val outcome = repo.register("Ana Souza", "52998224725", "ana@example.com", "+5511999998888", "Correct-Horse-Battery-9")

        assertEquals(LoginOutcome.Done, outcome)
        assertEquals("rt-3", store.token)
        val register = server.takeRequest().body.readUtf8()
        assertTrue(register.contains("+5511999998888"))
    }
}

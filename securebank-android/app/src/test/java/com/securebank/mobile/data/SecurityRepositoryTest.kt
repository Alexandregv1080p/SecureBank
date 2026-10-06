package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.FakeTokenStore
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.session.SessionManager
import com.securebank.mobile.core.session.fakeJwt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurityRepositoryTest {
    private val server = MockWebServer()
    private lateinit var security: SecurityRepository
    private lateinit var banking: BankingRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun setUp() {
        server.start()
        val session = SessionManager().also { it.start(fakeJwt()) }
        val network = NetworkFactory.create(server.url("/api/v1/").toString(), FakeTokenStore("rt"), session, "SecureBank-Android/test")
        security = SecurityRepository(network.security, network.json)
        banking = BankingRepository(network.banking, network.json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mfaStatusAndSetup() = runBlocking {
        server.enqueue(json("""{"enabled":false}"""))
        server.enqueue(json("""{"secret":"JBSWY3DPEHPK3PXP","otpauthUri":"otpauth://totp/SecureBank:ana?secret=JBSWY3DPEHPK3PXP"}"""))

        assertFalse(security.mfaEnabled())
        val setup = security.setupMfa()

        assertEquals("JBSWY3DPEHPK3PXP", setup.secret)
        assertTrue(setup.otpauthUri.startsWith("otpauth://"))
        assertEquals("/api/v1/security/mfa", server.takeRequest().path)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
    }

    @Test
    fun confirmAndDisableSendTheCodeInTheBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        security.confirmMfa("123456")
        security.disableMfa("654321") // DELETE com corpo

        val confirm = server.takeRequest()
        assertEquals("/api/v1/security/mfa/confirm", confirm.path)
        assertTrue(confirm.body.readUtf8().contains("\"code\":\"123456\""))
        val disable = server.takeRequest()
        assertEquals("DELETE", disable.method)
        assertEquals("/api/v1/security/mfa", disable.path)
        assertTrue(disable.body.readUtf8().contains("\"code\":\"654321\""))
    }

    @Test
    fun aWrongMfaCodeIsAFriendlyError() {
        server.enqueue(json("""{"status":422,"code":"INVALID_MFA_CODE","message":"x","traceId":"t"}""", 422))

        val e = runCatching { runBlocking { security.confirmMfa("000000") } }.exceptionOrNull() as ApiError

        assertEquals("INVALID_MFA_CODE", e.code)
    }

    @Test
    fun changingThePasswordSignalsThatSessionsChanged() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(2000) { security.changes.first() } }

        security.changePassword("Senha-Atual-12345", "Nova-Senha-Longa-9")

        assertNotNull(seen.await())
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"currentPassword\"") && body.contains("\"newPassword\""))
    }

    @Test
    fun aRefusedPasswordChangeDoesNotSignalAnything() = runBlocking {
        server.enqueue(json("""{"status":422,"code":"INVALID_CURRENT_PASSWORD","message":"x","traceId":"t"}""", 422))
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(300) { security.changes.first() } }

        val e = runCatching { security.changePassword("errada", "Nova-Senha-Longa-9") }.exceptionOrNull() as ApiError

        assertEquals("INVALID_CURRENT_PASSWORD", e.code)
        assertNull(seen.await())
    }

    @Test
    fun sessionsAreListedAndRevoked() = runBlocking {
        server.enqueue(json("""[{"id":"s1","createdAt":"2026-10-02T10:00:00Z","lastUsedAt":"2026-10-02T11:00:00Z","expiresAt":"2026-10-09T10:00:00Z","ip":"203.0.x.x","userAgent":"SecureBank-Android/0.1.0","mfaVerified":true,"current":true},{"id":"s2","createdAt":"2026-10-01T10:00:00Z","lastUsedAt":"2026-10-01T11:00:00Z","expiresAt":"2026-10-08T10:00:00Z","current":false}]"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val list = security.sessions()
        security.revokeSession("s2")

        assertEquals(2, list.size)
        assertTrue(list[0].current)
        assertNull(list[1].ip) // IP é opcional
        assertEquals("/api/v1/security/sessions", server.takeRequest().path)
        val revoke = server.takeRequest()
        assertEquals("DELETE", revoke.method)
        assertEquals("/api/v1/security/sessions/s2", revoke.path)
    }

    @Test
    fun notificationsArePagedAndMarkedAsRead() = runBlocking {
        server.enqueue(json("""{"items":[{"id":"n1","type":"TRANSFER","title":"Transferência enviada","body":"R$ 10,00","createdAt":"2026-10-02T14:30:00Z","read":false}],"page":1,"size":20,"totalElements":21}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val page = banking.notifications(1, 20)
        banking.markRead("n1")

        assertFalse(page.items[0].read)
        assertEquals(21L, page.totalElements)
        assertEquals("/api/v1/notifications?page=1&size=20", server.takeRequest().path)
        val read = server.takeRequest()
        assertEquals("POST", read.method)
        assertEquals("/api/v1/notifications/n1/read", read.path)
    }
}

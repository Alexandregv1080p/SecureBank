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

class PixRepositoryTest {
    private val server = MockWebServer()
    private lateinit var banking: BankingRepository
    private lateinit var pix: PixRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val entry = """{"id":"x1","endToEndId":"E00000000202610021430abcdefghijk","sourceAccountId":"a1","amount":{"amount":"120.50","currency":"BRL"},"message":"almoço","key":"bob@example.com","counterpartName":"Bob L***","direction":"SENT","createdAt":"2026-10-02T14:30:00Z"}"""

    @Before
    fun setUp() {
        server.start()
        val session = SessionManager().also { it.start(fakeJwt()) }
        val network = NetworkFactory.create(server.url("/api/v1/").toString(), FakeTokenStore("rt"), session, "SecureBank-Android/test")
        banking = BankingRepository(network.banking, network.json)
        pix = PixRepository(network.pix, network.json, banking)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun keysAreListedRegisteredByTypeOnlyAndDeleted() = runBlocking {
        server.enqueue(json("""[{"id":"k1","accountId":"a1","type":"EMAIL","key":"ana@example.com","createdAt":"2026-10-02T14:30:00Z"}]"""))
        server.enqueue(json("""{"id":"k2","accountId":"a1","type":"CPF","key":"52998224725","createdAt":"2026-10-02T14:30:00Z"}""", 201))
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals("ana@example.com", pix.keys()[0].key)
        pix.registerKey("a1", "CPF")
        pix.deleteKey("k2")

        assertEquals("/api/v1/pix/keys", server.takeRequest().path)
        val register = server.takeRequest()
        assertEquals("""{"accountId":"a1","type":"CPF"}""", register.body.readUtf8()) // o app NÃO manda o valor da chave
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/pix/keys/k2", delete.path)
    }

    @Test
    fun lookupEncodesTheKeyAndReturnsOnlyMaskedData() = runBlocking {
        server.enqueue(json("""{"type":"EMAIL","key":"bob+pix@example.com","name":"Bob L***","document":"***.456.789-**","bank":"SecureBank","ownAccount":false}"""))

        val found = pix.lookup("  bob+pix@example.com ")

        assertEquals("Bob L***", found.name)
        assertEquals("***.456.789-**", found.document)
        assertFalse(found.ownAccount)
        assertEquals("/api/v1/pix/keys/lookup?key=bob%2Bpix%40example.com", server.takeRequest().path) // o "+" vira %2B (não some como espaço)
    }

    @Test
    fun anUnknownKeyIsNotFound() {
        server.enqueue(json("""{"status":404,"code":"NOT_FOUND","message":"x","traceId":"t"}""", 404))

        val e = runCatching { runBlocking { pix.lookup("ninguem@example.com") } }.exceptionOrNull() as ApiError

        assertEquals(404, e.status)
    }

    @Test
    fun sendCarriesTheIdempotencyKeyAndTheAmountAsString() = runBlocking {
        server.enqueue(json(entry, 201))

        val sent = pix.send("a1", " bob@example.com ", "120.50", "almoço", "key-pix-00001")

        assertTrue(sent.sent)
        assertEquals("Bob L***", sent.counterpartName)
        val request = server.takeRequest()
        assertEquals("/api/v1/pix/transfers", request.path)
        assertEquals("key-pix-00001", request.getHeader("Idempotency-Key"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"amount\":\"120.50\"") && body.contains("\"key\":\"bob@example.com\""))
    }

    @Test
    fun anEmptyMessageIsNotSent() = runBlocking {
        server.enqueue(json(entry, 201))

        pix.send("a1", "bob@example.com", "10.00", null, "key-pix-00002")

        assertFalse(server.takeRequest().body.readUtf8().contains("message"))
    }

    @Test
    fun historyIsPagedAndKnowsTheDirection() = runBlocking {
        server.enqueue(json("""{"items":[$entry,${entry.replace("SENT", "RECEIVED")}],"page":1,"size":20,"totalElements":22}"""))

        val page = pix.history(1, 20)

        assertEquals(22L, page.totalElements)
        assertTrue(page.items[0].sent)
        assertFalse(page.items[1].sent)
        assertEquals("/api/v1/pix/transfers?page=1&size=20", server.takeRequest().path)
    }

    @Test
    fun aSuccessfulSendTellsTheScreensToReloadButAFailedOneDoesNot() = runBlocking {
        server.enqueue(json(entry, 201))
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(2000) { banking.changes.first() } }
        pix.send("a1", "bob@example.com", "1.00", null, "key-pix-00003")
        assertNotNull(seen.await())

        server.enqueue(json("""{"status":422,"code":"INSUFFICIENT_FUNDS","message":"x","traceId":"t"}""", 422))
        val none = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(300) { banking.changes.first() } }
        val e = runCatching { pix.send("a1", "bob@example.com", "9999.00", null, "key-pix-00004") }.exceptionOrNull() as ApiError
        assertEquals("INSUFFICIENT_FUNDS", e.code)
        assertNull(none.await())
    }
}

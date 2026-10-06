package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.FakeTokenStore
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.network.PixEntry
import com.securebank.mobile.core.session.SessionManager
import com.securebank.mobile.core.session.fakeJwt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
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

    // ---------------------------------------------------------------- devolução
    @Test
    fun refundSendsTheKeyAndAnOptionalAmount() = runBlocking {
        server.enqueue(json(entry.replace("SENT", "SENT").replace("\"direction\"", "\"refundOfId\":\"orig-1\",\"direction\""), 201))
        server.enqueue(json(entry, 201))

        val refund = pix.refund("orig-1", "30.00", "key-refund-001")
        pix.refund("orig-1", null, "key-refund-002")

        assertTrue(refund.isRefund)
        val first = server.takeRequest()
        assertEquals("/api/v1/pix/transfers/orig-1/refund", first.path)
        assertEquals("key-refund-001", first.getHeader("Idempotency-Key"))
        assertEquals("""{"amount":"30.00"}""", first.body.readUtf8())
        assertEquals("{}", server.takeRequest().body.readUtf8()) // sem valor: o servidor devolve o que resta
    }

    @Test
    fun aReceivedPixKnowsHowMuchCanStillBeRefunded() = runBlocking {
        val received = entry.replace("SENT", "RECEIVED").replace("\"createdAt\"", "\"refundedAmount\":{\"amount\":\"20.00\",\"currency\":\"BRL\"},\"refundableAmount\":{\"amount\":\"100.50\",\"currency\":\"BRL\"},\"createdAt\"")
        server.enqueue(json(received))

        val e = pix.transfer("x1")

        assertTrue(e.canRefund)
        assertEquals("100.50", e.refundableAmount?.amount)
        assertFalse(e.sent)
        assertEquals("/api/v1/pix/transfers/x1", server.takeRequest().path)
    }

    @Test
    fun sentPixAndRefundsAreNeverRefundable() {
        val sent = Json { ignoreUnknownKeys = true }.decodeFromString<PixEntry>(entry)
        val noRoom = entry.replace("SENT", "RECEIVED").replace("\"createdAt\"", "\"refundableAmount\":{\"amount\":\"0.00\",\"currency\":\"BRL\"},\"createdAt\"")

        assertFalse(sent.canRefund)
        assertFalse(Json { ignoreUnknownKeys = true }.decodeFromString<PixEntry>(noRoom).canRefund)
    }

    // ---------------------------------------------------------------- cobrança
    private val txid = "AbCdEfGhIjKlMnOpQrStUvWxYz012345"

    @Test
    fun aChargeIsCreatedViewedPaidAndCanceled() = runBlocking {
        server.enqueue(json("""{"txid":"$txid","accountId":"a1","amount":{"amount":"75.00","currency":"BRL"},"description":"Pedido 42","status":"ACTIVE","expiresAt":"2026-10-03T14:30:00Z","createdAt":"2026-10-02T14:30:00Z","location":"pix.securebank.example/charges/$txid"}""", 201))
        server.enqueue(json("""{"txid":"$txid","amount":{"amount":"75.00","currency":"BRL"},"description":"Pedido 42","status":"ACTIVE","expiresAt":"2026-10-03T14:30:00Z","receiverName":"Ana S***","receiverDocument":"***.123.***-**","own":false,"bank":"SecureBank"}"""))
        server.enqueue(json(entry, 201))
        server.enqueue(MockResponse().setResponseCode(204))

        val created = pix.createCharge("a1", "75.00", "Pedido 42", 60)
        val view = pix.charge(txid)
        pix.payCharge(txid, "a2", "key-charge-01")
        pix.cancelCharge(txid)

        assertEquals("pix.securebank.example/charges/$txid", created.location)
        assertEquals("Ana S***", view.receiverName)
        assertFalse(view.own)
        val create = server.takeRequest()
        assertEquals("/api/v1/pix/charges", create.path)
        assertTrue(create.body.readUtf8().contains("\"expiresInMinutes\":60"))
        assertEquals("/api/v1/pix/charges/$txid", server.takeRequest().path)
        val pay = server.takeRequest()
        assertEquals("/api/v1/pix/charges/$txid/pay", pay.path)
        assertEquals("key-charge-01", pay.getHeader("Idempotency-Key"))
        assertEquals("""{"sourceAccountId":"a2"}""", pay.body.readUtf8())
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun anExpiredChargeAndAnUnknownOneAreDistinguishable() {
        server.enqueue(json("""{"status":422,"code":"PIX_CHARGE_EXPIRED","message":"x","traceId":"t"}""", 422))
        server.enqueue(json("""{"status":404,"code":"NOT_FOUND","message":"x","traceId":"t"}""", 404))

        val expired = runCatching { runBlocking { pix.payCharge(txid, "a1", "key-charge-02") } }.exceptionOrNull() as ApiError
        val unknown = runCatching { runBlocking { pix.charge(txid) } }.exceptionOrNull() as ApiError

        assertEquals("PIX_CHARGE_EXPIRED", expired.code)
        assertEquals(404, unknown.status)
    }

    // ---------------------------------------------------------------- agendados
    @Test
    fun aScheduleSendsTheDateAsIsoAndNothingMovesYet() = runBlocking {
        server.enqueue(json("""{"id":"s1","sourceAccountId":"a1","key":"bob@example.com","destinationName":"Bob L***","amount":{"amount":"80.00","currency":"BRL"},"message":"aluguel","scheduledFor":"2026-10-20","status":"SCHEDULED","createdAt":"2026-10-06T14:30:00Z"}""", 201))

        val s = pix.schedule("a1", " bob@example.com ", "80.00", "aluguel", "2026-10-20", "key-sched-0001")

        assertEquals("SCHEDULED", s.status)
        assertEquals("2026-10-20", s.scheduledFor)
        val request = server.takeRequest()
        assertEquals("/api/v1/pix/schedules", request.path)
        assertEquals("key-sched-0001", request.getHeader("Idempotency-Key"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"scheduledFor\":\"2026-10-20\"") && body.contains("\"key\":\"bob@example.com\""))
    }

    @Test
    fun schedulesAreListedWithTheirFailureReasonAndCanceled() = runBlocking {
        server.enqueue(json("""{"items":[{"id":"s1","sourceAccountId":"a1","key":"k","destinationName":"Bob L***","amount":{"amount":"80.00","currency":"BRL"},"scheduledFor":"2026-10-05","status":"FAILED","failureReason":"INSUFFICIENT_FUNDS","createdAt":"2026-10-01T10:00:00Z"}],"page":0,"size":20,"totalElements":1}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val page = pix.schedules(0, 20)
        pix.cancelSchedule("s2")

        assertEquals("INSUFFICIENT_FUNDS", page.items[0].failureReason)
        assertEquals("/api/v1/pix/schedules?page=0&size=20", server.takeRequest().path)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/pix/schedules/s2", delete.path)
    }
}

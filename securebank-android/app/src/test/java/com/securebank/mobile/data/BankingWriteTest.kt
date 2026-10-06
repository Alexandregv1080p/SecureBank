package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.FakeTokenStore
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.network.PaymentRequest
import com.securebank.mobile.core.network.TransferRequest
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BankingWriteTest {
    private val server = MockWebServer()
    private lateinit var repo: BankingRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val transaction = """{"id":"t1","type":"DEPOSIT","direction":"CREDIT","amount":{"amount":"50.00","currency":"BRL"},"balanceAfter":{"amount":"150.00","currency":"BRL"},"status":"COMPLETED","createdAt":"2026-10-02T14:30:00Z"}"""

    @Before
    fun setUp() {
        server.start()
        val session = SessionManager().also { it.start(fakeJwt()) }
        val network = NetworkFactory.create(server.url("/api/v1/").toString(), FakeTokenStore("rt"), session, "SecureBank-Android/test")
        repo = BankingRepository(network.banking, network.json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun depositAndWithdrawSendTheKeyAndTheAmountAsString() = runBlocking {
        server.enqueue(json(transaction, 201))
        server.enqueue(json(transaction, 201))

        repo.deposit("a1", "50.00", "key-dep-0001")
        repo.withdraw("a1", "20.50", "key-wd-0002")

        val dep = server.takeRequest()
        assertEquals("/api/v1/accounts/a1/deposits", dep.path)
        assertEquals("key-dep-0001", dep.getHeader("Idempotency-Key"))
        assertTrue(dep.body.readUtf8().contains("\"amount\":\"50.00\"")) // string, nunca número
        val wd = server.takeRequest()
        assertEquals("/api/v1/accounts/a1/withdrawals", wd.path)
        assertEquals("key-wd-0002", wd.getHeader("Idempotency-Key"))
    }

    @Test
    fun transferAndPaymentBodiesOmitEmptyDescription() = runBlocking {
        server.enqueue(json("""{"id":"x","sourceAccountId":"a1","destinationAccountId":"a2","amount":{"amount":"10.00","currency":"BRL"},"status":"COMPLETED","createdAt":"2026-10-02T14:30:00Z"}""", 201))
        server.enqueue(json("""{"id":"p","accountId":"a1","amount":{"amount":"99.90","currency":"BRL"},"barcode":"1","status":"COMPLETED","createdAt":"2026-10-02T14:30:00Z"}""", 201))

        repo.transfer(TransferRequest("a1", "0001", "123456-7", "10.00"), "key-tr-00001")
        repo.pay(PaymentRequest("a1", "99.90", "1".repeat(44)), "key-pay-0001")

        val transfer = server.takeRequest()
        assertEquals("/api/v1/transfers", transfer.path)
        assertEquals("key-tr-00001", transfer.getHeader("Idempotency-Key"))
        val body = transfer.body.readUtf8()
        assertTrue(body, !body.contains("description")) // null não vai no corpo
        assertEquals("key-pay-0001", server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun aSuccessfulOperationSignalsThatBalancesChanged() = runBlocking {
        server.enqueue(json(transaction, 201))
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(2000) { repo.changes.first() } }

        repo.deposit("a1", "50.00", "key-dep-0001")

        assertNotNull(seen.await())
    }

    @Test
    fun aFailedOperationDoesNotSignalAChange() = runBlocking {
        server.enqueue(json("""{"status":422,"code":"INSUFFICIENT_FUNDS","message":"x","traceId":"t"}""", 422))
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(300) { repo.changes.first() } }

        val e = runCatching { repo.withdraw("a1", "999999.00", "key-wd-0003") }.exceptionOrNull() as ApiError

        assertEquals("INSUFFICIENT_FUNDS", e.code)
        assertNull(seen.await())
    }

    @Test
    fun replayedResponsesAreJustSuccesses() = runBlocking {
        // a API devolve a resposta original com Idempotency-Replayed: true; para o app é um sucesso comum
        server.enqueue(json(transaction, 201).setHeader("Idempotency-Replayed", "true"))

        assertEquals("t1", repo.deposit("a1", "50.00", "key-dep-0001").id)
    }
}

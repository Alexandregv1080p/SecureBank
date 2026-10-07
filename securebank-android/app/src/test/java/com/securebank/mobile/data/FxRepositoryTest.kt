package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.FakeTokenStore
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.session.SessionManager
import com.securebank.mobile.core.session.fakeJwt
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FxRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: FxRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val operation = """{"id":"o1","accountId":"a1","side":"BUY","foreignAmount":{"amount":"100.00","currency":"USD"},"rate":"5.278000","brlAmount":{"amount":"527.80","currency":"BRL"},"createdAt":"2026-10-06T14:30:00Z","extra":1}"""

    @Before
    fun setUp() {
        server.start()
        val session = SessionManager().also { it.start(fakeJwt()) }
        val network = NetworkFactory.create(server.url("/api/v1/").toString(), FakeTokenStore("rt"), session, "SecureBank-Android/test")
        repo = FxRepository(network.fx, network.json, BankingRepository(network.banking, network.json))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun ratesAndWalletsAreParsed() = runBlocking {
        server.enqueue(json("""[{"currency":"USD","mid":"5.200000","buyRate":"5.278000","sellRate":"5.122000","spreadPercent":"1.5","updatedAt":"2026-10-06T14:30:00Z"}]"""))
        server.enqueue(json("""[{"currency":"USD","balance":{"amount":"60.00","currency":"USD"}}]"""))

        val rates = repo.rates()
        val wallets = repo.wallets()

        assertEquals("5.278000", rates.single().buyRate)
        assertEquals("USD", wallets.single().balance.currency)
    }

    @Test
    fun buySendsTheQuotedRateAndTheKey() = runBlocking {
        server.enqueue(json(operation, 201))

        val op = repo.buy("a1", "USD", "100.00", "5.278000", "key-fx-buy-001")

        assertTrue(op.bought)
        assertEquals("527.80", op.brlAmount.amount)
        val request = server.takeRequest()
        assertEquals("/api/v1/fx/buy", request.path)
        assertEquals("key-fx-buy-001", request.getHeader("Idempotency-Key"))
        assertEquals("""{"accountId":"a1","currency":"USD","amount":"100.00","quotedRate":"5.278000"}""", request.body.readUtf8())
    }

    @Test
    fun sellUsesItsOwnPathAndOperationsArePaged() = runBlocking {
        server.enqueue(json(operation.replace("BUY", "SELL"), 201))
        server.enqueue(json("""{"items":[$operation],"page":0,"size":10,"totalElements":1}"""))

        val sold = repo.sell("a1", "USD", "40.00", "5.122000", "key-fx-sell-001")
        val page = repo.operations()

        assertEquals("SELL", sold.side)
        assertEquals("/api/v1/fx/sell", server.takeRequest().path)
        assertEquals("/api/v1/fx/operations?page=0&size=10", server.takeRequest().path)
        assertEquals(1L, page.totalElements)
    }

    @Test
    fun aChangedRateIsADistinctBusinessError() {
        server.enqueue(json("""{"status":422,"code":"FX_RATE_CHANGED","message":"x","traceId":"t"}""", 422))

        val e = runCatching { runBlocking { repo.buy("a1", "USD", "10.00", "5.100000", "key-fx-buy-002") } }.exceptionOrNull() as ApiError

        assertEquals("FX_RATE_CHANGED", e.code)
    }
}

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InvestmentRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: InvestmentRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun money(v: String) = """{"amount":"$v","currency":"BRL"}"""

    private val investment = """{"id":"i1","accountId":"a1","productCode":"CDB_90","productName":"CDB 90 dias","principal":${money("1000.00")},"annualRatePercent":"11.50","termDays":90,"appliedAt":"2026-10-01T10:00:00Z","maturesAt":"2026-12-30T10:00:00Z","status":"ACTIVE","daysHeld":10,"gross":${money("1003.00")},"yield":${money("3.00")},"tax":${money("0.68")},"taxRatePercent":"22.50","net":${money("1002.32")},"canRedeem":false,"campoNovo":1}"""

    @Before
    fun setUp() {
        server.start()
        val session = SessionManager().also { it.start(fakeJwt()) }
        val network = NetworkFactory.create(server.url("/api/v1/").toString(), FakeTokenStore("rt"), session, "SecureBank-Android/test")
        repo = InvestmentRepository(network.investment, network.json, BankingRepository(network.banking, network.json))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun productsAndInvestmentsAreParsedIgnoringUnknownFields() = runBlocking {
        server.enqueue(json("""[{"code":"CDB_DAILY","name":"CDB Liquidez Diária","kind":"DAILY","annualRatePercent":"10.50","minAmount":"1.00"}]"""))
        server.enqueue(json("[$investment]"))

        val products = repo.products()
        val list = repo.list()

        assertEquals(null, products.single().termDays)
        assertEquals("1002.32", list.single().net.amount)
        assertTrue(list.single().active)
        assertFalse(list.single().canRedeem)
    }

    @Test
    fun applySendsTheKeyAndTheBody() = runBlocking {
        server.enqueue(json(investment, 201))

        repo.apply("a1", "CDB_90", "1000.00", "key-invest-001")

        val request = server.takeRequest()
        assertEquals("/api/v1/investments", request.path)
        assertEquals("key-invest-001", request.getHeader("Idempotency-Key"))
        assertEquals("""{"accountId":"a1","productCode":"CDB_90","amount":"1000.00"}""", request.body.readUtf8())
    }

    @Test
    fun redeemSendsTheKeyAndNoBody() = runBlocking {
        server.enqueue(json(investment.replace("ACTIVE", "REDEEMED"), 201))

        val redeemed = repo.redeem("i1", "key-redeem-01")

        assertFalse(redeemed.active)
        val request = server.takeRequest()
        assertEquals("/api/v1/investments/i1/redeem", request.path)
        assertEquals("key-redeem-01", request.getHeader("Idempotency-Key"))
    }

    @Test
    fun businessErrorsKeepTheirCode() {
        server.enqueue(json("""{"status":422,"code":"INVESTMENT_NOT_MATURED","message":"x","traceId":"t"}""", 422))

        val e = runCatching { runBlocking { repo.redeem("i1", "key-redeem-02") } }.exceptionOrNull() as ApiError

        assertEquals("INVESTMENT_NOT_MATURED", e.code)
    }
}

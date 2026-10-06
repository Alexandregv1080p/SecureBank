package com.securebank.mobile.data

import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.FakeTokenStore
import com.securebank.mobile.core.network.NetworkFactory
import com.securebank.mobile.core.session.SessionManager
import com.securebank.mobile.core.session.fakeJwt
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BankingRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: BankingRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val account = """{"id":"a1","branch":"0001","accountNumber":"123456-7","type":"CHECKING","status":"ACTIVE","balance":{"amount":"1500.50","currency":"BRL"},"createdAt":"2026-10-01T10:00:00Z","novoCampo":1}"""

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
    fun accountsParseAndIgnoreFieldsTheAppDoesNotKnow() = runBlocking {
        server.enqueue(json("[$account]"))

        val accounts = repo.accounts()

        assertEquals(1, accounts.size)
        assertEquals("1500.50", accounts[0].balance.amount) // dinheiro continua String
        assertEquals("/api/v1/accounts", server.takeRequest().path)
    }

    @Test
    fun statementSendsPagingAndTheDayRangeOnlyWhenSet() = runBlocking {
        server.enqueue(json("""{"items":[],"page":0,"size":20,"totalElements":0}"""))
        server.enqueue(json("""{"items":[],"page":2,"size":20,"totalElements":0}"""))

        repo.statement("a1", page = 0)
        repo.statement("a1", page = 2, from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31))

        assertEquals("/api/v1/accounts/a1/statement?page=0&size=20", server.takeRequest().path)
        assertEquals("/api/v1/accounts/a1/statement?page=2&size=20&from=2026-10-01&to=2026-10-31", server.takeRequest().path)
    }

    @Test
    fun statementItemsKeepTheirMoneyAsStrings() = runBlocking {
        server.enqueue(
            json(
                """{"items":[{"id":"t1","type":"TRANSFER","direction":"DEBIT","amount":{"amount":"10.00","currency":"BRL"},
                |"balanceAfter":{"amount":"90.00","currency":"BRL"},"status":"COMPLETED","reference":null,"createdAt":"2026-10-02T14:30:00Z"}],
                |"page":0,"size":20,"totalElements":1}""".trimMargin(),
            ),
        )

        val page = repo.statement("a1", 0)

        assertEquals("10.00", page.items[0].amount.amount)
        assertEquals(1L, page.totalElements)
    }

    @Test
    fun openAccountPostsTheType() = runBlocking {
        server.enqueue(json(account, 201))

        val opened = repo.openAccount("SAVINGS")

        assertEquals("123456-7", opened.accountNumber)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("\"type\":\"SAVINGS\""))
    }

    @Test
    fun anotherOwnersAccountLooksLikeNotFound() {
        server.enqueue(json("""{"status":404,"code":"NOT_FOUND","message":"x","traceId":"t"}""", 404))

        val e = runCatching { runBlocking { repo.account("de-outro") } }.exceptionOrNull() as ApiError

        assertEquals(404, e.status)
        assertEquals("NOT_FOUND", e.code)
    }

    @Test
    fun statementSendsCategoryAndDirectionFilters() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"items":[],"page":0,"size":20,"totalElements":0}"""))

        repo.statement("acc1", 0, category = "PIX", direction = "CREDIT")

        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.contains("category=PIX") && path.contains("direction=CREDIT"))
    }

    @Test
    fun theMonthlySummaryIsReadByMonth() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"month":"2026-10","income":{"amount":"520.00","currency":"BRL"},"expenses":{"amount":"150.00","currency":"BRL"},"net":{"amount":"370.00","currency":"BRL"},"byCategory":[{"category":"CASH","income":{"amount":"500.00","currency":"BRL"},"expenses":{"amount":"50.00","currency":"BRL"}}]}"""))

        val summary = repo.statementSummary("acc1", java.time.YearMonth.of(2026, 10))

        assertEquals("370.00", summary.net.amount)
        assertEquals("CASH", summary.byCategory.single().category)
        assertEquals("/api/v1/accounts/acc1/statement/summary?month=2026-10", server.takeRequest().path)
    }

    @Test
    fun exportReturnsTheCsvTextWithTheSameFilters() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/csv").setBody("date,type\r\nx,DEPOSIT\r\n"))

        val csv = repo.exportStatement("acc1", java.time.LocalDate.of(2026, 10, 1), null, "CASH", null)

        assertEquals("date,type\r\nx,DEPOSIT\r\n", csv)
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.contains("/statement/export") && path.contains("from=2026-10-01") && path.contains("category=CASH") && !path.contains("direction"))
    }

    @Test
    fun anExportFailureIsAnApiError() {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("""{"status":404,"code":"NOT_FOUND","message":"x","traceId":"t"}"""))

        val e = runCatching { runBlocking { repo.exportStatement("acc1") } }.exceptionOrNull() as ApiError

        assertEquals(404, e.status)
    }
}

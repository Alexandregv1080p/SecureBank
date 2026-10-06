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
}

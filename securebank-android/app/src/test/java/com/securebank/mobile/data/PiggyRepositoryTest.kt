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

class PiggyRepositoryTest {
    private val server = MockWebServer()
    private lateinit var banking: BankingRepository
    private lateinit var piggies: PiggyRepository

    private fun json(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private val piggy = """{"id":"p1","accountId":"a1","name":"Viagem","balance":{"amount":"100.00","currency":"BRL"},"goal":{"amount":"150.00","currency":"BRL"},"progressPercent":66,"goalReached":false,"status":"ACTIVE","createdAt":"2026-10-02T14:30:00Z"}"""

    @Before
    fun setUp() {
        server.start()
        val session = SessionManager().also { it.start(fakeJwt()) }
        val network = NetworkFactory.create(server.url("/api/v1/").toString(), FakeTokenStore("rt"), session, "SecureBank-Android/test")
        banking = BankingRepository(network.banking, network.json)
        piggies = PiggyRepository(network.piggy, network.json, banking)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listParsesPiggiesWithAndWithoutGoal() = runBlocking {
        server.enqueue(json("[$piggy,{\"id\":\"p2\",\"accountId\":\"a1\",\"name\":\"Reserva\",\"balance\":{\"amount\":\"0.00\",\"currency\":\"BRL\"},\"goalReached\":false,\"status\":\"ACTIVE\",\"createdAt\":\"2026-10-02T14:30:00Z\"}]"))

        val list = piggies.list()

        assertEquals(2, list.size)
        assertEquals(66, list[0].progressPercent)
        assertEquals("150.00", list[0].goal?.amount)
        assertNull(list[1].goal)
        assertNull(list[1].progressPercent)
        assertEquals("/api/v1/piggies", server.takeRequest().path)
    }

    @Test
    fun createTrimsTheNameAndOmitsAMissingGoal() = runBlocking {
        server.enqueue(json(piggy, 201))
        server.enqueue(json(piggy, 201))

        piggies.create("a1", "  Viagem  ", "150.00")
        piggies.create("a1", "Reserva", null)

        val withGoal = server.takeRequest().body.readUtf8()
        assertTrue(withGoal, withGoal.contains("\"name\":\"Viagem\"") && withGoal.contains("\"goal\":\"150.00\""))
        val withoutGoal = server.takeRequest().body.readUtf8()
        assertFalse(withoutGoal, withoutGoal.contains("goal")) // null não vai no corpo
    }

    @Test
    fun saveAndRedeemSendTheKeyAndTheAmountAsString() = runBlocking {
        server.enqueue(json(piggy, 201))
        server.enqueue(json(piggy, 201))

        piggies.save("p1", "50.00", "key-save-0001")
        piggies.redeem("p1", "20.50", "key-redeem-02")

        val save = server.takeRequest()
        assertEquals("/api/v1/piggies/p1/deposits", save.path)
        assertEquals("key-save-0001", save.getHeader("Idempotency-Key"))
        assertTrue(save.body.readUtf8().contains("\"amount\":\"50.00\""))
        val redeem = server.takeRequest()
        assertEquals("/api/v1/piggies/p1/withdrawals", redeem.path)
        assertEquals("key-redeem-02", redeem.getHeader("Idempotency-Key"))
    }

    @Test
    fun updatesOnlySendTheChangedFieldAndClearGoalIsExplicit() = runBlocking {
        server.enqueue(json(piggy))
        server.enqueue(json(piggy))
        server.enqueue(json(piggy))

        piggies.rename("p1", " Novo ")
        piggies.setGoal("p1", "300.00")
        piggies.clearGoal("p1")

        val rename = server.takeRequest()
        assertEquals("PATCH", rename.method)
        assertEquals("""{"name":"Novo"}""", rename.body.readUtf8())
        assertEquals("""{"goal":"300.00"}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"clearGoal":true}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun closeIsADelete() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        piggies.close("p1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/piggies/p1", request.path)
    }

    @Test
    fun everyMutationTellsTheScreensToReload() = runBlocking {
        server.enqueue(json(piggy, 201))
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(2000) { banking.changes.first() } }

        piggies.save("p1", "10.00", "key-save-0001")

        assertNotNull(seen.await()) // o saldo da conta e a lista de porquinhos recarregam juntos
    }

    @Test
    fun aRefusedOperationDoesNotNotifyAndKeepsTheBusinessCode() = runBlocking {
        server.enqueue(json("""{"status":422,"code":"INSUFFICIENT_PIGGY_FUNDS","message":"x","traceId":"t"}""", 422))
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(300) { banking.changes.first() } }

        val e = runCatching { piggies.redeem("p1", "999.00", "key-redeem-03") }.exceptionOrNull() as ApiError

        assertEquals("INSUFFICIENT_PIGGY_FUNDS", e.code)
        assertNull(seen.await())
    }
}

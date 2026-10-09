package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.Money as ApiMoney
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationTest {
    // ---- IdempotentIntent
    private data class Pay(val value: String)

    private fun intent(): IdempotentIntent {
        var n = 0
        return IdempotentIntent(IdempotencyKeys { "key-${++n}" })
    }

    @Test
    fun anUncertainFailureKeepsTheKeySoTheRetryCannotDuplicate() = runBlocking {
        val intent = intent()
        val keys = mutableListOf<String>()

        for (uncertain in listOf(ApiError(0, "NETWORK", "x", network = true), ApiError(503, "SERVICE_UNAVAILABLE", "x"), ApiError(409, "CONFLICT", "x"))) {
            runCatching { intent.run(Pay("10.00")) { key -> keys += key; throw uncertain } }
        }
        intent.run(Pay("10.00")) { key -> keys += key }

        assertEquals(1, keys.toSet().size) // todas as tentativas usaram a MESMA chave
    }

    @Test
    fun aBusinessErrorReleasesTheKey() = runBlocking {
        val intent = intent()
        val keys = mutableListOf<String>()

        runCatching { intent.run(Pay("10.00")) { key -> keys += key; throw ApiError(422, "INSUFFICIENT_FUNDS", "x") } }
        runCatching { intent.run(Pay("10.00")) { key -> keys += key } }

        assertNotEquals(keys[0], keys[1]) // erro definitivo: nova intenção, nova chave
    }

    @Test
    fun successReleasesTheKeyAndADifferentRequestGetsADifferentKey() = runBlocking {
        val intent = intent()
        val keys = mutableListOf<String>()

        intent.run(Pay("10.00")) { keys += it }
        intent.run(Pay("10.00")) { keys += it }
        runCatching { intent.run(Pay("20.00")) { key -> keys += key; throw ApiError(0, "NETWORK", "x", network = true) } }
        intent.run(Pay("30.00")) { keys += it }

        assertEquals(4, keys.toSet().size)
    }

    @Test
    fun cancellationKeepsTheKeyBecauseTheRequestMayHaveLeft() = runBlocking {
        val intent = intent()
        val keys = mutableListOf<String>()

        runCatching { intent.run(Pay("10.00")) { key -> keys += key; throw CancellationException("tela fechou") } }
        intent.run(Pay("10.00")) { keys += it }

        assertEquals(keys[0], keys[1])
    }

    // ---- validação
    @Test
    fun transferRules() {
        assertTrue(OperationValidation.transfer("a1", "0001", "123456-0", "10,50", "").isEmpty())
        assertEquals(setOf(TransferField.Source), OperationValidation.transfer(null, "0001", "123456-0", "10", "").keys)
        assertEquals(setOf(TransferField.Branch), OperationValidation.transfer("a1", "001", "123456-0", "10", "").keys)
        assertEquals(setOf(TransferField.Number), OperationValidation.transfer("a1", "0001", "1234567", "10", "").keys)
        assertEquals(setOf(TransferField.Number), OperationValidation.transfer("a1", "0001", "12345-7", "10", "").keys)
        assertEquals(setOf(TransferField.Amount), OperationValidation.transfer("a1", "0001", "123456-0", "0", "").keys)
        assertEquals(setOf(TransferField.Description), OperationValidation.transfer("a1", "0001", "123456-0", "10", "x".repeat(141)).keys)
    }

    @Test
    fun paymentRules() {
        val ok44 = "1".repeat(44)
        assertTrue(OperationValidation.payment("a1", ok44, "10", "").isEmpty())
        assertTrue(OperationValidation.payment("a1", "1".repeat(47), "10", "").isEmpty())
        assertTrue(OperationValidation.payment("a1", "1".repeat(48), "10", "").isEmpty())
        // linha digitável com pontos e espaços: só os dígitos contam (47)
        assertTrue(OperationValidation.payment("a1", "12345.67890 12345.678901 12345.678901 1 23456789012345", "10", "").isEmpty())
        assertEquals(setOf(PaymentField.Barcode), OperationValidation.payment("a1", "123", "10", "").keys)
        assertEquals(setOf(PaymentField.Account), OperationValidation.payment("", ok44, "10", "").keys)
        assertEquals(setOf(PaymentField.Amount), OperationValidation.payment("a1", ok44, "abc", "").keys)
    }

    @Test
    fun amountMessageIsSharedByAllForms() {
        assertEquals("O valor deve ser maior que zero", OperationValidation.amount("0"))
        assertEquals("Informe o valor", OperationValidation.amount(""))
        assertEquals(null, OperationValidation.amount("1.234,56"))
    }

    private val source = Account("a1", "0001", "100048-9", "CHECKING", "ACTIVE", ApiMoney("300.00", "BRL"), "2026-10-01T10:00:00Z")
    private fun limit(per: String, remaining: String) =
        LimitUsage("PAYMENT", ApiMoney(per, "BRL"), ApiMoney("5000.00", "BRL"), ApiMoney("0.00", "BRL"), ApiMoney(remaining, "BRL"))

    @Test
    fun paymentsAndPixCheckBalanceAndLimitBeforeAskingForTheBiometric() {
        val ok44 = "1".repeat(44)
        assertTrue(OperationValidation.payment("a1", ok44, "300", "", source, limit("1000.00", "1000.00")).isEmpty())
        assertEquals(setOf(PaymentField.Amount), OperationValidation.payment("a1", ok44, "300,01", "", source, null).keys) // saldo
        assertEquals(setOf(PaymentField.Amount), OperationValidation.payment("a1", ok44, "200", "", source, limit("100.00", "1000.00")).keys) // por operação
        assertEquals(setOf(PaymentField.Amount), OperationValidation.payment("a1", ok44, "200", "", source, limit("1000.00", "150.00")).keys) // restante hoje
        assertTrue(PixValidation.send("a1", "300", "", source, limit("1000.00", "1000.00")).isEmpty())
        assertEquals(setOf(PixField.Amount), PixValidation.send("a1", "300,01", "", source, null).keys)
        // sem conta nem limite carregados, o servidor decide
        assertTrue(PixValidation.send("a1", "99999", "").isEmpty())
    }

    @Test
    fun theLimitHintNeedsALoadedLimit() {
        assertEquals(null, Validation.limitHint(null))
        assertTrue(Validation.limitHint(limit("1000.00", "250.00"))!!.contains("250,00"))
    }
}

package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.Money as ApiMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardValidationTest {
    private fun limit(perOperation: String, remaining: String) =
        LimitUsage("CARD", ApiMoney(perOperation, "BRL"), ApiMoney("5000.00", "BRL"), ApiMoney("0.00", "BRL"), ApiMoney(remaining, "BRL"))

    @Test
    fun theNumberIsGroupedByFourAndTheExpiryHasTwoDigits() {
        assertEquals("9123 4567 8901 2345", CardValidation.formatNumber("9123456789012345"))
        assertEquals("9123 4567", CardValidation.formatNumber("9123 4567"))
        assertEquals("07/31", CardValidation.formatExpiry(7, 2031))
        assertEquals("12/30", CardValidation.formatExpiry(12, 2030))
    }

    @Test
    fun theMerchantIsRequiredShortAndWithoutControlCharacters() {
        assertNull(CardValidation.merchantIssue("Mercado Central"))
        assertNull(CardValidation.merchantIssue("Café & Cia"))
        assertEquals("Informe o estabelecimento", CardValidation.merchantIssue("   "))
        assertEquals("No máximo 60 caracteres", CardValidation.merchantIssue("x".repeat(61)))
        assertNotNull(CardValidation.merchantIssue("Loja" + 10.toChar() + "linha 2"))
        assertNotNull(CardValidation.merchantIssue("Loja" + 0x202E.toChar() + "evil"))
    }

    @Test
    fun thePurchaseChecksValueBalanceAndLimit() {
        assertNull(CardValidation.amountIssue("50,00", "100.00", limit("3000.00", "1000.00")))
        assertEquals("Informe o valor", CardValidation.amountIssue("", "100.00", limit("3000.00", "1000.00")))
        assertTrue(CardValidation.amountIssue("100,01", "100.00", limit("3000.00", "1000.00"))!!.startsWith("Saldo insuficiente"))
        assertTrue(CardValidation.amountIssue("3.000,01", "9999.00", limit("3000.00", "1000.00"))!!.startsWith("Acima do limite por operação"))
        assertTrue(CardValidation.amountIssue("1.000,01", "9999.00", limit("3000.00", "1000.00"))!!.startsWith("Acima do que resta"))
        assertNull(CardValidation.amountIssue("10", null, null)) // sem saldo nem limite carregados, o servidor decide
    }

    @Test
    fun theStatusHasAPortugueseLabel() {
        assertEquals("Ativo", CardValidation.statusLabel("ACTIVE"))
        assertEquals("Bloqueado", CardValidation.statusLabel("BLOCKED"))
        assertEquals("Cancelado", CardValidation.statusLabel("CANCELED"))
    }

    @Test
    fun blockAndCancelNeedAReason() {
        val lost = CardValidation.blockReasons.first()
        val other = CardValidation.blockReasons.first { it.other }
        assertEquals("Escolha o motivo", CardValidation.reasonIssue(null, ""))
        assertNull(CardValidation.reasonIssue(lost, "")) // uma opção basta
        assertTrue(CardValidation.reasonIssue(other, "")!!.startsWith("Conte o motivo"))
        assertTrue(CardValidation.reasonIssue(other, "  ab ")!!.startsWith("Conte o motivo"))
        assertNull(CardValidation.reasonIssue(other, "Roubaram na rua"))
        assertEquals("No máximo 140 caracteres", CardValidation.reasonIssue(other, "x".repeat(141)))
        assertEquals("No máximo 140 caracteres no total", CardValidation.reasonIssue(lost, "x".repeat(140)))
    }

    @Test
    fun theReasonSentIsTheStoryOrTheOptionWithTheDetail() {
        val stolen = CardValidation.cancelReasons.first()
        val other = CardValidation.cancelReasons.first { it.other }
        assertEquals("O cartão foi perdido ou roubado", CardValidation.composeReason(stolen, ""))
        assertEquals("O cartão foi perdido ou roubado: deixei no táxi", CardValidation.composeReason(stolen, "  deixei no   táxi "))
        assertEquals("troquei de banco", CardValidation.composeReason(other, "  troquei  de banco "))
    }
}

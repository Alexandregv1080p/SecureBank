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
}

package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditValidationTest {
    @Test
    fun theMonthAndDatesAreInPortuguese() {
        assertEquals("novembro de 2026", CreditValidation.monthLabel("2026-11"))
        assertEquals("janeiro de 2027", CreditValidation.monthLabel("2027-01"))
        assertEquals("10/11", CreditValidation.shortDate("2026-11-10"))
    }

    @Test
    fun installmentsAreWholeCentsAndTheLastTakesTheRemainder() {
        assertEquals(listOf(3333L, 3333L, 3334L), CreditValidation.installmentAmounts(10000, 3))
        assertEquals(listOf(10000L), CreditValidation.installmentAmounts(10000, 1))
        assertEquals(10L, CreditValidation.installmentAmounts(10, 3).sum())
    }

    @Test
    fun onlyInstallmentsAboveTheMinimumAreOffered() {
        fun ns(v: String) = CreditValidation.installmentOptions(v).map { it.n }
        assertEquals(listOf(1), ns(""))
        assertEquals(listOf(1, 2, 3, 4), ns("20,00")) // 5x seria R$ 4,00
        assertEquals(listOf(1, 2, 3, 4, 5), ns("25"))
        assertEquals(12, ns("1.000,00").size)
        assertEquals("3x de R$ 33,33 (última R$ 33,34), sem juros", CreditValidation.installmentOptions("100,00")[2].label.replace('\u00a0', ' '))
    }

    @Test
    fun thePurchaseChecksLimitAndMinimumInstallment() {
        assertNull(CreditValidation.purchaseAmountIssue("500,00", 3, "2000.00"))
        assertTrue(CreditValidation.purchaseAmountIssue("2.000,01", 1, "2000.00")!!.startsWith("Passa do limite disponível"))
        assertTrue(CreditValidation.purchaseAmountIssue("20,00", 5, "2000.00")!!.contains("parcela mínima"))
        assertEquals("Informe o valor", CreditValidation.purchaseAmountIssue("", 1, null))
        assertNull(CreditValidation.purchaseAmountIssue("10", 1, null)) // sem limite carregado, o servidor decide
    }

    @Test
    fun payingGoesFromOneCentToWhatIsLeft() {
        assertNull(CreditValidation.payIssue("150,00", "250.00"))
        assertNull(CreditValidation.payIssue("250,00", "250.00"))
        assertTrue(CreditValidation.payIssue("250,01", "250.00")!!.contains("passa do que falta"))
        assertEquals("Informe o valor", CreditValidation.payIssue("", "250.00"))
        assertNotNull(CreditValidation.payIssue("0", "250.00"))
    }

    @Test
    fun theHintFollowsTheInvoiceStatus() {
        assertEquals("Fecha em 03/11 e vence em 10/11", CreditValidation.dueHint("OPEN", "2026-11-03", "2026-11-10"))
        assertEquals("Fechada: vence em 10/11", CreditValidation.dueHint("CLOSED", "2026-11-03", "2026-11-10"))
        assertTrue(CreditValidation.dueHint("OVERDUE", "2026-11-03", "2026-11-10").startsWith("Venceu em 10/11"))
        assertEquals("Fatura paga", CreditValidation.dueHint("PAID", "2026-11-03", "2026-11-10"))
    }
}

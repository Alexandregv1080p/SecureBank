package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class PixValidationTest {
    @Test
    fun theKeyCannotBeBlank() {
        assertNotNull(PixValidation.keyError("  "))
        assertNull(PixValidation.keyError("ana@example.com"))
    }

    @Test
    fun aValidSendHasNoErrors() {
        assertTrue(PixValidation.send("a1", "10,50", "almoço").isEmpty())
        assertTrue(PixValidation.send("a1", "10", "").isEmpty())
    }

    @Test
    fun eachRuleReportsItsOwnField() {
        assertEquals(setOf(PixField.Source), PixValidation.send(null, "10", "").keys)
        assertEquals(setOf(PixField.Amount), PixValidation.send("a1", "0", "").keys)
        assertEquals(setOf(PixField.Message), PixValidation.send("a1", "10", "x".repeat(141)).keys)
    }

    @Test
    fun keyTypesHaveFriendlyLabels() {
        assertEquals("CPF", PixValidation.typeLabel("CPF"))
        assertEquals("E-mail", PixValidation.typeLabel("EMAIL"))
        assertEquals("Celular", PixValidation.typeLabel("PHONE"))
        assertEquals("Chave aleatória", PixValidation.typeLabel("RANDOM"))
    }

    private val today = LocalDate.of(2026, 10, 6)

    @Test
    fun theScheduleDateMustBeFromTomorrowUpTo365DaysAhead() {
        assertNull(PixValidation.scheduleDateError(today.plusDays(1), today))
        assertNull(PixValidation.scheduleDateError(today.plusDays(365), today))
        assertNotNull(PixValidation.scheduleDateError(null, today))
        assertNotNull(PixValidation.scheduleDateError(today, today))
        assertNotNull(PixValidation.scheduleDateError(today.minusDays(1), today))
        assertNotNull(PixValidation.scheduleDateError(today.plusDays(366), today))
    }

    @Test
    fun brazilianDatesAreParsedStrictly() {
        assertEquals(LocalDate.of(2026, 10, 20), PixValidation.parseDate(" 20/10/2026 "))
        assertNull(PixValidation.parseDate("31/02/2026"))
        assertNull(PixValidation.parseDate("2026-10-20"))
        assertNull(PixValidation.parseDate("20/10/26"))
        assertNull(PixValidation.parseDate(""))
        assertEquals("20/10/2026", PixValidation.displayDate("2026-10-20"))
        assertEquals("lixo", PixValidation.displayDate("lixo"))
    }

    @Test
    fun aRefundCannotBeZeroOrMoreThanWhatRemains() {
        assertNull(PixValidation.refundAmountError("60,00", "60.00"))
        assertNull(PixValidation.refundAmountError("0,01", "60.00"))
        assertNotNull(PixValidation.refundAmountError("60,01", "60.00"))
        assertNotNull(PixValidation.refundAmountError("0", "60.00"))
        assertNotNull(PixValidation.refundAmountError("abc", "60.00"))
        assertTrue(PixValidation.refundAmountError("100", "60.00")!!.contains("60,00"))
    }

    @Test
    fun statusesAndFailuresAreInPlainLanguage() {
        assertEquals("Agendado", PixValidation.scheduleStatusLabel("SCHEDULED"))
        assertEquals("Realizado", PixValidation.scheduleStatusLabel("EXECUTED"))
        assertEquals("Não realizado", PixValidation.scheduleStatusLabel("FAILED"))
        assertEquals("Cancelado", PixValidation.scheduleStatusLabel("CANCELED"))
        assertEquals("saldo insuficiente", PixValidation.scheduleFailure("INSUFFICIENT_FUNDS"))
        assertEquals("a chave não existe mais", PixValidation.scheduleFailure("NOT_FOUND"))
        assertEquals("motivo desconhecido", PixValidation.scheduleFailure(null))
        assertEquals("Aguardando pagamento", PixValidation.chargeStatusLabel("ACTIVE"))
        assertEquals("Expirada", PixValidation.chargeStatusLabel("EXPIRED"))
        assertEquals("Paga", PixValidation.chargeStatusLabel("PAID"))
    }
}

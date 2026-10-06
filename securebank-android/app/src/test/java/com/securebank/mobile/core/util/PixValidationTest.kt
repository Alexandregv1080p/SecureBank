package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}

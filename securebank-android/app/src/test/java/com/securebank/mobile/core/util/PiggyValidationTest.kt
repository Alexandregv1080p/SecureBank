package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PiggyValidationTest {
    @Test
    fun aValidPiggyHasNoErrors() {
        assertTrue(PiggyValidation.validate("a1", "Viagem", "1.500,00").isEmpty())
        assertTrue(PiggyValidation.validate("a1", "Viagem", "").isEmpty()) // meta é opcional
        assertTrue(PiggyValidation.validate("a1", "x".repeat(40), "").isEmpty())
    }

    @Test
    fun eachRuleReportsItsOwnField() {
        assertEquals(setOf(PiggyField.Account), PiggyValidation.validate(null, "Viagem", "").keys)
        assertEquals(setOf(PiggyField.Name), PiggyValidation.validate("a1", "  ", "").keys)
        assertEquals(setOf(PiggyField.Name), PiggyValidation.validate("a1", "x".repeat(41), "").keys)
        assertEquals(setOf(PiggyField.Goal), PiggyValidation.validate("a1", "Viagem", "0").keys)
        assertEquals(setOf(PiggyField.Goal), PiggyValidation.validate("a1", "Viagem", "abc").keys)
    }

    @Test
    fun goalIsNormalizedOrNull() {
        assertEquals("1500.00", PiggyValidation.goalOrNull("1.500,00"))
        assertNull(PiggyValidation.goalOrNull(""))
        assertNull(PiggyValidation.goalOrNull("   "))
    }
}

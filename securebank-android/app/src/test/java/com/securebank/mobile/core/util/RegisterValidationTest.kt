package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterValidationTest {
    private fun validate(
        name: String = "Ana Souza",
        document: String = "529.982.247-25",
        email: String = "ana@example.com",
        phone: String = "11 99999-8888",
        password: String = "Correct-Horse-Battery-9",
        confirm: String = password,
    ) = RegisterValidation.validate(name, document, email, phone, password, confirm)

    @Test
    fun aValidFormHasNoErrors() {
        assertTrue(validate().isEmpty())
    }

    @Test
    fun eachRuleReportsItsOwnField() {
        assertEquals(setOf(RegisterField.Name), validate(name = "A").keys)
        assertEquals(setOf(RegisterField.Document), validate(document = "123").keys)
        assertEquals(setOf(RegisterField.Email), validate(email = "ana@").keys)
        assertEquals(setOf(RegisterField.Email), validate(email = "").keys)
        assertEquals(setOf(RegisterField.Phone), validate(phone = "1199").keys)
        assertEquals(setOf(RegisterField.Password), validate(password = "curta", confirm = "curta").keys)
        assertEquals(setOf(RegisterField.Confirm), validate(confirm = "outra-senha-qualquer").keys)
    }
}

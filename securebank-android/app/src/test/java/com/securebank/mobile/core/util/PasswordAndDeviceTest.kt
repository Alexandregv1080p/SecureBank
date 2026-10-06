package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordAndDeviceTest {
    @Test
    fun passwordRules() {
        assertTrue(PasswordValidation.validate("atual", "Nova-Senha-Longa-9", "Nova-Senha-Longa-9").isEmpty())
        assertEquals(setOf(PasswordField.Current), PasswordValidation.validate("", "Nova-Senha-Longa-9", "Nova-Senha-Longa-9").keys)
        assertEquals(setOf(PasswordField.Next), PasswordValidation.validate("atual", "curta", "curta").keys)
        assertEquals(setOf(PasswordField.Next), PasswordValidation.validate("atual", "x".repeat(129), "x".repeat(129)).keys)
        assertEquals(setOf(PasswordField.Confirm), PasswordValidation.validate("atual", "Nova-Senha-Longa-9", "outra").keys)
    }

    @Test
    fun devicesAreShownInPlainLanguage() {
        assertEquals("Dispositivo desconhecido", Format.device(null))
        assertEquals("Dispositivo desconhecido", Format.device("  "))
        assertEquals("App SecureBank Android 0.1.0", Format.device("SecureBank-Android/0.1.0"))
        assertEquals("Navegador (Firefox)", Format.device("Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"))
        assertEquals("Navegador (Edge)", Format.device("Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/129 Safari/537.36 Edg/129.0"))
        assertEquals("Navegador (Chrome)", Format.device("Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/129 Safari/537.36"))
        assertEquals("Navegador (Safari)", Format.device("Mozilla/5.0 (Macintosh) AppleWebKit/605.1.15 Version/17 Safari/605.1.15"))
        assertEquals("curl/8.5.0", Format.device("curl/8.5.0"))
        assertEquals(60, Format.device("x".repeat(200)).length)
    }
}

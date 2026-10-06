package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilTest {
    @Test
    fun parseAcceptsBrazilianAndPlainFormats() {
        assertEquals("1234.56", Money.parse("1.234,56"))
        assertEquals("1234.56", Money.parse("1234.56"))
        assertEquals("10", Money.parse("10"))
        assertEquals("0.5", Money.parse("R$ 0,5"))
    }

    @Test
    fun parseRejectsZeroNegativeTooManyDecimalsAndGarbage() {
        for (bad in listOf("", "0", "0,00", "-5", "10,001", "abc", "1,2,3", "12345678901234")) {
            assertNull("deveria recusar '$bad'", Money.parse(bad))
        }
    }

    @Test
    fun formatShowsReais() {
        assertEquals("R$ 1.234,50", Money.format("1234.5").replace('\u00a0', ' '))
    }

    @Test
    fun phoneNormalizesBrazilianNumbers() {
        assertEquals("+5511999998888", Phone.toE164BR("11 99999-8888"))
        assertEquals("+5511999998888", Phone.toE164BR("+55 (11) 99999-8888"))
        assertNull(Phone.toE164BR("1199"))
    }

    @Test
    fun idempotencyKeyIsReusedOnlyForTheSameRequestUntilSettled() {
        var n = 0
        val keys = IdempotencyKeys { "key-${++n}" }

        val first = keys.keyFor("amount=10.00")
        assertEquals(first, keys.keyFor("amount=10.00")) // retry após erro incerto
        assertNotEquals(first, keys.keyFor("amount=20.00")) // outro pedido, outra chave

        val current = keys.keyFor("amount=20.00")
        keys.settle()
        assertNotEquals(current, keys.keyFor("amount=20.00")) // resultado definitivo: intenção nova
    }
}

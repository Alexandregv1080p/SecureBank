package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.Money as ApiMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    private fun usage(per: String, remaining: String) =
        LimitUsage("TRANSFER", ApiMoney(per, "BRL"), ApiMoney("5000.00", "BRL"), ApiMoney("0.00", "BRL"), ApiMoney(remaining, "BRL"))

    @Test
    fun theAccountCheckDigitIsTheServersModulo11() {
        // contas reais geradas pelo servidor
        listOf("100048-9", "100049-7", "100051-9", "000001-9").forEach { assertTrue(it, Validation.accountNumberValid(it)) }
        // um dígito errado, ou formato errado
        listOf("100048-8", "100049-0", "100051-1", "12345-6", "1000489", "abc123-4", "").forEach { assertFalse(it, Validation.accountNumberValid(it)) }
    }

    @Test
    fun theSameAccountIsDetectedOnlyWithBranchAndNumber() {
        assertTrue(Validation.sameAccount("0001", "100048-9", "0001", "100048-9"))
        assertFalse(Validation.sameAccount("0001", "100048-9", "0002", "100048-9"))
        assertFalse(Validation.sameAccount("0001", "100048-9", "0001", "100049-7"))
    }

    @Test
    fun typingAnAmountKeepsOnlyReaisAndCents() {
        assertEquals("", Validation.sanitizeAmount(""))
        assertEquals("", Validation.sanitizeAmount("abc"))
        assertEquals("1250,5", Validation.sanitizeAmount("1250,5"))
        assertEquals("12,34", Validation.sanitizeAmount("12,345"))
        assertEquals("1234,56", Validation.sanitizeAmount("1.234,56")) // colado com milhar
        assertEquals("1,5", Validation.sanitizeAmount("1.5")) // ponto decimal vira vírgula
        assertEquals("7", Validation.sanitizeAmount("007"))
        assertEquals("0,5", Validation.sanitizeAmount(",5"))
        assertEquals("0", Validation.sanitizeAmount("0"))
        assertEquals("1,2", Validation.sanitizeAmount("1,,2")) // só uma vírgula
        assertEquals(13, Validation.sanitizeAmount("12345678901234567").length)
    }

    @Test
    fun anInvalidAmountSaysWhy() {
        assertNull(Validation.amountIssue("1.234,56"))
        assertNull(Validation.amountIssue("10"))
        assertEquals("Informe o valor", Validation.amountIssue(""))
        assertEquals("Informe o valor", Validation.amountIssue("  "))
        assertEquals("O valor deve ser maior que zero", Validation.amountIssue("0,00"))
        assertEquals("Use no máximo 2 casas decimais", Validation.amountIssue("1,234"))
        assertEquals("Use no máximo 2 casas decimais", Validation.amountIssue("1.500"))
        assertEquals("Esse valor é alto demais", Validation.amountIssue("12345678901234"))
        assertNotNull(Validation.amountIssue("1,2,3"))
        assertNotNull(Validation.amountIssue("abc"))
    }

    @Test
    fun theAmountIsComparedWithBalanceAndLimitsExactly() {
        assertNull(Validation.balanceIssue("100.00", "100.00"))
        assertTrue(Validation.balanceIssue("100.01", "100.00")!!.startsWith("Saldo insuficiente"))
        assertNull(Validation.limitIssue("100.00", null)) // sem limite carregado, o servidor decide
        assertNull(Validation.limitIssue("1000.00", usage("1000.00", "1000.00")))
        assertTrue(Validation.limitIssue("1000.01", usage("1000.00", "5000.00"))!!.startsWith("Acima do limite por operação"))
        assertTrue(Validation.limitIssue("600.00", usage("1000.00", "500.00"))!!.startsWith("Acima do que resta"))
    }

    @Test
    fun theCpfNeedsValidCheckDigits() {
        assertTrue(Validation.cpfValid("529.982.247-25"))
        assertTrue(Validation.cpfValid("52998224725"))
        assertFalse(Validation.cpfValid("529.982.247-24"))
        assertFalse(Validation.cpfValid("111.111.111-11"))
        assertFalse(Validation.cpfValid("123"))
    }

    @Test
    fun maskingFollowsTheDigits() {
        assertEquals("", Validation.maskCpf(""))
        assertEquals("529.98", Validation.maskCpf("52998"))
        assertEquals("529.982.247-25", Validation.maskCpf("52998224725999"))
        assertEquals("(11)", Validation.maskPhone("11"))
        assertEquals("(1", Validation.maskPhone("1"))
        assertEquals("(11) 9999", Validation.maskPhone("119999"))
        assertEquals("(11) 9999-8888", Validation.maskPhone("1199998888"))
        assertEquals("(11) 99999-8888", Validation.maskPhone("11999998888"))
        assertEquals("(11) 99999-8888", Validation.maskPhone("+55 11 99999-8888"))
        assertEquals("", Validation.maskPhone(""))
    }

    @Test
    fun theAccountHyphenAppearsBeforeTheCheckDigit() {
        assertEquals("", Validation.maskAccountNumber(""))
        assertEquals("12345", Validation.maskAccountNumber("12345"))
        assertEquals("123456", Validation.maskAccountNumber("123456"))
        assertEquals("123456-0", Validation.maskAccountNumber("1234560"))
        assertEquals("1234567-8", Validation.maskAccountNumber("12345678"))
        assertEquals("123456-0", Validation.maskAccountNumber("123456-0")) // digitado com hífen: igual
        assertEquals("123456789012-3", Validation.maskAccountNumber("1234567890123456"))
    }

    @Test
    fun theNameNeedsASurnameAndOnlyLetters() {
        assertNull(Validation.fullNameIssue("Maria da Silva"))
        assertNull(Validation.fullNameIssue("Ana D'Ávila-Souza"))
        assertEquals("Informe nome e sobrenome", Validation.fullNameIssue("Maria"))
        assertEquals("Informe nome e sobrenome", Validation.fullNameIssue("  "))
        assertEquals("Use só letras no nome", Validation.fullNameIssue("Maria 123"))
    }

    @Test
    fun thePasswordFollowsTheServerPolicy() {
        assertNull(Validation.passwordIssue("Correct-Horse-Battery-9", "marina@example.com"))
        assertEquals("Use de 12 a 128 caracteres", Validation.passwordIssue("curta"))
        assertEquals("Use de 12 a 128 caracteres", Validation.passwordIssue("a".repeat(129)))
        assertEquals("Essa senha é fácil de adivinhar", Validation.passwordIssue("senha1234567"))
        assertEquals("Essa senha é fácil de adivinhar", Validation.passwordIssue("aaaabbbbaaaa"))
        assertEquals("A senha não pode conter o seu e-mail", Validation.passwordIssue("xx-marina-xx-2026", "Marina@example.com"))
        assertNull(Validation.passwordIssue("xx-ana-xx-2026-ok", "ana@example.com")) // parte local curta não conta
    }
}

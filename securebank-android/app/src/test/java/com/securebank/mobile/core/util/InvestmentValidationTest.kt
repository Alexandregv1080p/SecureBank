package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.InvestmentProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentValidationTest {
    private val cdb90 = InvestmentProduct("CDB_90", "CDB 90 dias", "TERM", "11.50", 90, "100.00")

    @Test
    fun theAmountMustBeValidAndAtLeastTheMinimum() {
        assertNull(InvestmentValidation.amountError("100,00", cdb90))
        assertNull(InvestmentValidation.amountError("2500", cdb90))
        assertNotNull(InvestmentValidation.amountError("99,99", cdb90))
        assertTrue(InvestmentValidation.amountError("50", cdb90)!!.contains("100,00"))
        assertEquals("Use só números e vírgula para os centavos, ex.: 1250,50", InvestmentValidation.amountError("abc", cdb90))
        assertEquals("O valor deve ser maior que zero", InvestmentValidation.amountError("0", cdb90))
        assertEquals("Informe o valor", InvestmentValidation.amountError("", cdb90))
        // saldo da conta
        assertNull(InvestmentValidation.amountError("400", cdb90, "400.00"))
        assertEquals("O valor passa do saldo da conta", InvestmentValidation.amountError("500", cdb90, "400.00"))
    }

    @Test
    fun labelsArePlainPortuguese() {
        assertEquals("Liquidez diária", InvestmentValidation.termLabel(null))
        assertEquals("Prazo de 90 dias", InvestmentValidation.termLabel(90))
        assertEquals("10,50% ao ano", InvestmentValidation.rateLabel("10.50"))
    }
}

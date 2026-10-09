package com.securebank.mobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxValidationTest {

    @Test
    fun buyingRoundsTheCostUpAndSellingRoundsTheProceedsDownLikeTheServer() {
        assertEquals("527.80", FxValidation.buyCost("100,00", "5.278000"))
        assertEquals("0.06", FxValidation.buyCost("0,01", "5.278000")) // 0,05278 sobe
        assertEquals("512.20", FxValidation.sellProceeds("100", "5.122000"))
        assertEquals("5.07", FxValidation.sellProceeds("0,99", "5.122000")) // 5,07078 desce
        assertNull(FxValidation.buyCost("abc", "5.278000"))
        assertNull(FxValidation.sellProceeds("", "5.122000"))
        assertNull(FxValidation.buyCost("10", "x"))
    }

    @Test
    fun theAmountMustBePositiveAndNotAboveTheWalletWhenSelling() {
        assertNull(FxValidation.amountError("10,50"))
        assertNotNull(FxValidation.amountError("0"))
        assertNotNull(FxValidation.amountError("1,234"))
        assertNull(FxValidation.amountError("40", "40.00"))
        assertNotNull(FxValidation.amountError("40,01", "40.00"))
        assertEquals("Informe o valor", FxValidation.amountError("", "40.00"))
    }

    @Test
    fun labelsAreInPlainPortuguese() {
        assertEquals("Dólar americano", FxValidation.currencyName("USD"))
        assertEquals("Euro", FxValidation.currencyName("EUR"))
        assertEquals("JPY", FxValidation.currencyName("JPY"))
        assertEquals("Compra", FxValidation.sideLabel("BUY"))
        assertEquals("Venda", FxValidation.sideLabel("SELL"))
        assertEquals("5,278", FxValidation.rateLabel("5.278000"))
        assertEquals("5,20", FxValidation.rateLabel("5.200000"))
        assertEquals("5,7348", FxValidation.rateLabel("5.734750"))
    }

    @Test
    fun foreignAmountsShowTheirSymbol() {
        assertTrue(FxValidation.format("USD", "1234.50").startsWith("US$ "))
        assertTrue(FxValidation.format("EUR", "10.00").startsWith("€ "))
        assertTrue(FxValidation.format("USD", "1234.50").contains("1.234,50"))
    }
}

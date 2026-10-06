package com.securebank.mobile.core.pix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
    @Test
    fun theMatrixIsSquareAndHasDarkModules() {
        val matrix = QrCode.matrix(BrCode.encode("ana@example.com", "Ana Souza", amount = "10.50"))

        assertTrue(matrix.size >= 21) // menor versão de QR code
        assertTrue(matrix.all { it.size == matrix.size })
        assertTrue(matrix.any { row -> row.any { it } })
    }

    @Test
    fun sameTextGivesTheSameQr() {
        val text = BrCode.encode("ana@example.com", "Ana")
        val a = QrCode.matrix(text)
        val b = QrCode.matrix(text)

        assertEquals(a.size, b.size)
        assertTrue(a.indices.all { a[it].contentEquals(b[it]) })
    }

    @Test
    fun finderPatternsMarkTheThreeCorners() {
        val m = QrCode.matrix("Pix")
        val last = m.size - 1

        assertTrue(m[0][0] && m[0][last] && m[last][0]) // cantos do padrão de localização
    }
}

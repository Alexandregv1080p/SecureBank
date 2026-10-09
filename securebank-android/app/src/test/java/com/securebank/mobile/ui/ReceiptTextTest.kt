package com.securebank.mobile.ui

import com.securebank.mobile.ui.components.ReceiptLine
import com.securebank.mobile.ui.components.receiptText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTextTest {
    @Test
    fun theSharedReceiptHasTheAmountTheLinesAndNoTrailingBlank() {
        val text = receiptText(
            "Transferência enviada",
            "250.50",
            listOf(ReceiptLine("Para", "Ana S***"), ReceiptLine("Identificador", "abc-123")),
        )
        val lines = text.lines()
        assertEquals("SecureBank · Transferência enviada", lines[0])
        assertTrue(lines[1].contains("250,50"))
        assertEquals("Para: Ana S***", lines[2])
        assertEquals("Identificador: abc-123", lines[3])
        assertEquals(4, lines.size)
    }
}

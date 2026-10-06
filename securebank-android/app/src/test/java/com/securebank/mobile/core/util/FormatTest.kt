package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.Money as ApiMoney
import com.securebank.mobile.core.network.Transaction
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    private fun tx(type: String, direction: String) = Transaction(
        id = "t", type = type, direction = direction, amount = ApiMoney("1.00"), balanceAfter = ApiMoney("2.00"),
        status = "COMPLETED", createdAt = "2026-10-02T17:30:00Z",
    )

    @Test
    fun totalBalanceSumsExactlyWithoutFloatingPointDrift() {
        assertEquals("0.30", Money.sum(listOf("0.10", "0.20"))) // 0.1 + 0.2 em double daria 0.30000000000000004
        assertEquals("1234.56", Money.sum(listOf("1000.00", "234.56")))
        assertEquals("0.00", Money.sum(emptyList()))
    }

    @Test
    fun labelsMatchTheWebApp() {
        assertEquals("Conta corrente", Format.accountTypeLabel("CHECKING"))
        assertEquals("Poupança", Format.accountTypeLabel("SAVINGS"))
        assertEquals("Ag. 0001, conta 123456-7", Format.accountLabel("0001", "123456-7"))
        assertEquals("Transferência", Format.limitLabel("TRANSFER"))
        assertEquals("Pix", Format.limitLabel("PIX"))
    }

    @Test
    fun transactionsAreDescribedByTypeAndDirection() {
        assertEquals("Depósito", Format.describe(tx("DEPOSIT", "CREDIT")))
        assertEquals("Saque", Format.describe(tx("WITHDRAW", "DEBIT")))
        assertEquals("Transferência recebida", Format.describe(tx("TRANSFER", "CREDIT")))
        assertEquals("Transferência enviada", Format.describe(tx("TRANSFER", "DEBIT")))
        assertEquals("Pagamento", Format.describe(tx("PAYMENT", "DEBIT")))
        assertEquals("Estorno", Format.describe(tx("REFUND", "CREDIT")))
        assertEquals("Pix enviado", Format.describe(tx("PIX_OUT", "DEBIT")))
        assertEquals("Pix recebido", Format.describe(tx("PIX_IN", "CREDIT")))
        assertEquals("Devolução de Pix enviada", Format.describe(tx("PIX_RETURN_OUT", "DEBIT")))
        assertEquals("Devolução de Pix recebida", Format.describe(tx("PIX_RETURN_IN", "CREDIT")))
        assertEquals("Guardado no porquinho", Format.describe(tx("PIGGY_IN", "DEBIT")))
        assertEquals("Resgate do porquinho", Format.describe(tx("PIGGY_OUT", "CREDIT")))
    }

    @Test
    fun dateTimeIsShownInTheGivenZone() {
        val shown = Format.dateTime("2026-10-02T17:30:00Z", ZoneId.of("America/Sao_Paulo")) // UTC-3
        assertTrue(shown, shown.contains("14:30"))
        assertTrue(shown, shown.startsWith("02"))
    }

    @Test
    fun firstNameIsTheFirstWord() {
        assertEquals("Ana", Format.firstName("  Ana Souza Lima "))
    }
}

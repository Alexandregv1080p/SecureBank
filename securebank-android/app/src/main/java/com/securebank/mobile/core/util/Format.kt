package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.Transaction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos de exibição (pt-BR), os mesmos do web. */
object Format {
    private val locale: Locale = Locale.forLanguageTag("pt-BR")
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM, HH:mm", locale)
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)

    fun accountTypeLabel(type: String) = if (type == "CHECKING") "Conta corrente" else "Poupança"

    fun accountLabel(branch: String, accountNumber: String) = "Ag. $branch, conta $accountNumber"

    fun limitLabel(type: String) = when (type) {
        "WITHDRAW" -> "Saque"
        "TRANSFER" -> "Transferência"
        "PAYMENT" -> "Pagamento"
        else -> type
    }

    fun describe(t: Transaction): String = when (t.type) {
        "DEPOSIT" -> "Depósito"
        "WITHDRAW" -> "Saque"
        "TRANSFER" -> if (t.direction == "CREDIT") "Transferência recebida" else "Transferência enviada"
        "PAYMENT" -> "Pagamento"
        else -> "Estorno"
    }

    /** ISO-8601 em UTC (como a API devolve) para o fuso do aparelho. */
    fun dateTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(Instant.parse(iso).atZone(zone))

    fun date(day: java.time.LocalDate): String = date.format(day)

    fun firstName(fullName: String): String = fullName.trim().substringBefore(' ')
}

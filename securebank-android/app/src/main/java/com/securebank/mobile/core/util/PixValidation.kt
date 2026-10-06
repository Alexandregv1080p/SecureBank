package com.securebank.mobile.core.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class PixField { Key, Source, Amount, Message }

object PixValidation {
    const val MESSAGE_MAX = 140

    fun keyError(input: String): String? = if (input.isBlank()) "Informe a chave Pix ou cole o código" else null

    fun send(sourceId: String?, amount: String, message: String): Map<PixField, String> = buildMap {
        if (sourceId.isNullOrEmpty()) put(PixField.Source, "Escolha a conta")
        OperationValidation.amount(amount)?.let { put(PixField.Amount, it) }
        if (message.length > MESSAGE_MAX) put(PixField.Message, "No máximo $MESSAGE_MAX caracteres")
    }

    const val MAX_SCHEDULE_DAYS = 365L

    private val brDate = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

    /** "20/10/2026" → data; qualquer outra coisa (inclusive 31/02) → null. */
    fun parseDate(input: String): LocalDate? = runCatching { LocalDate.parse(input.trim(), brDate) }.getOrNull()

    /** "2026-10-20" (formato do servidor) → "20/10/2026". */
    fun displayDate(iso: String): String = runCatching { LocalDate.parse(iso).format(brDate) }.getOrDefault(iso)

    /** O agendamento só vale do dia seguinte em diante, até 365 dias (mesma regra do servidor). */
    fun scheduleDateError(date: LocalDate?, today: LocalDate): String? = when {
        date == null -> "Escolha a data"
        !date.isAfter(today) -> "Escolha uma data a partir de amanhã"
        date.isAfter(today.plusDays(MAX_SCHEDULE_DAYS)) -> "No máximo $MAX_SCHEDULE_DAYS dias à frente"
        else -> null
    }

    /** Quanto se pode devolver: positivo e no máximo o que ainda resta (o servidor confere de novo). */
    fun refundAmountError(input: String, refundable: String): String? {
        val parsed = Money.parse(input) ?: return OperationValidation.AMOUNT_MESSAGE
        val max = refundable.toBigDecimalOrNull() ?: return null
        return if (parsed.toBigDecimal() > max) "O valor passa do que ainda pode ser devolvido (${Money.format(refundable)})" else null
    }

    /** Por que um Pix agendado não foi realizado (códigos do servidor), em linguagem de gente. */
    fun scheduleFailure(code: String?): String = when (code) {
        "INSUFFICIENT_FUNDS" -> "saldo insuficiente"
        "LIMIT_EXCEEDED" -> "limite do Pix excedido"
        "NOT_FOUND" -> "a chave não existe mais"
        "ACCOUNT_NOT_ACTIVE" -> "conta indisponível"
        "CUSTOMER_NOT_ACTIVE" -> "cadastro inativo"
        null -> "motivo desconhecido"
        else -> code
    }

    fun scheduleStatusLabel(status: String): String = when (status) {
        "SCHEDULED" -> "Agendado"
        "EXECUTED" -> "Realizado"
        "FAILED" -> "Não realizado"
        "CANCELED" -> "Cancelado"
        else -> status
    }

    fun chargeStatusLabel(status: String): String = when (status) {
        "ACTIVE" -> "Aguardando pagamento"
        "PAID" -> "Paga"
        "CANCELED" -> "Cancelada"
        "EXPIRED" -> "Expirada"
        else -> status
    }

    /** Rótulo do tipo de chave. */
    fun typeLabel(type: String): String = when (type) {
        "CPF" -> "CPF"
        "EMAIL" -> "E-mail"
        "PHONE" -> "Celular"
        "RANDOM" -> "Chave aleatória"
        else -> type
    }
}

package com.securebank.mobile.core.util

import java.math.BigDecimal

/** Regras do cartão de crédito (espelham securebank-web/src/lib/credit.ts; o servidor confere tudo de novo). */
object CreditValidation {
    val DUE_DAYS = listOf(5, 10, 15, 20, 25)
    const val MAX_INSTALLMENTS = 12
    /** Parcela mínima em centavos (R$ 5,00), como no servidor. */
    const val MIN_INSTALLMENT_CENTS = 500L
    const val CREDIT_LIMIT_START = "R$ 2.000,00"
    const val LIMIT_MIN = 100L
    const val LIMIT_SELF_SERVICE_MAX = 10_000L
    const val MAX_ADDITIONAL = 2

    fun chargeLabel(kind: String) = when (kind) {
        "LATE_FEE" -> "Multa por atraso (2%)"
        "INTEREST" -> "Juros por atraso"
        else -> kind
    }

    private val months = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

    fun invoiceStatusLabel(status: String) = when (status) {
        "OPEN" -> "Aberta"
        "CLOSED" -> "Fechada"
        "OVERDUE" -> "Vencida"
        "REVOLVING" -> "No rotativo"
        "PAID" -> "Paga"
        else -> status
    }

    /** "2026-11" vira "novembro de 2026". */
    fun monthLabel(month: String): String {
        val (year, m) = month.split("-").map { it.toInt() }
        return "${months.getOrElse(m - 1) { month }} de $year"
    }

    /** "2026-11-10" vira "10/11" (as datas da fatura vêm como dia, sem fuso). */
    fun shortDate(day: String): String {
        val parts = day.split("-")
        return "${parts[2]}/${parts[1]}"
    }

    /** Divide em parcelas de centavos inteiros; o resto vai para a última (igual ao servidor). */
    fun installmentAmounts(totalCents: Long, n: Int): List<Long> {
        val each = totalCents / n
        return List(n - 1) { each } + (totalCents - each * (n - 1))
    }

    data class InstallmentOption(val n: Int, val label: String)

    private fun cents(amount: String): Long = BigDecimal(amount).movePointRight(2).toLong()

    private fun brl(cents: Long): String = Money.format(BigDecimal(cents).movePointLeft(2).toPlainString())

    /** Opções "3x de R$ 100,00" que respeitam a parcela mínima, para o valor informado. */
    fun installmentOptions(input: String): List<InstallmentOption> {
        val value = Money.parse(input) ?: return listOf(InstallmentOption(1, "À vista"))
        val total = cents(value)
        val options = mutableListOf(InstallmentOption(1, "À vista, ${brl(total)}"))
        for (n in 2..MAX_INSTALLMENTS) {
            val parts = installmentAmounts(total, n)
            if (parts.first() < MIN_INSTALLMENT_CENTS) break
            val last = parts.last()
            val tail = if (last != parts.first()) " (última ${brl(last)})" else ""
            options += InstallmentOption(n, "${n}x de ${brl(parts.first())}$tail, sem juros")
        }
        return options
    }

    /** Valor da compra no crédito: positivo, dentro do limite disponível e com parcela mínima; `null` quando está tudo certo. */
    fun purchaseAmountIssue(input: String, installments: Int, available: String?): String? {
        val value = Money.parse(input) ?: return Validation.amountIssue(input)
        val total = cents(value)
        if (available != null && total > cents(available)) return "Passa do limite disponível (${Money.format(available)})"
        if (installments > 1 && installmentAmounts(total, installments).first() < MIN_INSTALLMENT_CENTS) return "A parcela mínima é R$ 5,00: use menos parcelas"
        return null
    }

    /** Valor a pagar da fatura: positivo e, no máximo, o que falta. */
    fun payIssue(input: String, remaining: String): String? {
        val value = Money.parse(input) ?: return Validation.amountIssue(input)
        return if (cents(value) > cents(remaining)) "O valor passa do que falta na fatura (${Money.format(remaining)})" else null
    }

    /** Novo limite: múltiplo de R$ 100, de R$ 100 a R$ 10.000 e nunca abaixo do que já está em uso. */
    fun limitIssue(input: String, used: String): String? {
        val value = Money.parse(input) ?: return Validation.amountIssue(input)
        val c = cents(value)
        return when {
            c % 10_000L != 0L -> "Use um valor múltiplo de R$ 100,00"
            c < LIMIT_MIN * 100 -> "O limite mínimo é ${brl(LIMIT_MIN * 100)}"
            c > LIMIT_SELF_SERVICE_MAX * 100 -> "Acima de ${brl(LIMIT_SELF_SERVICE_MAX * 100)} só com a equipe do banco"
            c < cents(used) -> "O limite não pode ficar abaixo do que já está em uso (${Money.format(used)})"
            else -> null
        }
    }

    /** Nome impresso no cartão adicional: 2 a 40 caracteres. */
    fun holderIssue(name: String): String? {
        val t = name.trim()
        return when {
            t.length < 2 -> "Informe o nome de quem vai usar o cartão"
            t.length > 40 -> "No máximo 40 caracteres"
            else -> null
        }
    }

    /** O que mostrar sobre a situação da fatura. */
    fun dueHint(status: String, closingDate: String, dueDate: String) = when (status) {
        "OPEN" -> "Fecha em ${shortDate(closingDate)} e vence em ${shortDate(dueDate)}"
        "CLOSED" -> "Fechada: vence em ${shortDate(dueDate)}"
        "OVERDUE" -> "Venceu em ${shortDate(dueDate)}: pague o mínimo para voltar a comprar"
        "REVOLVING" -> "Venceu em ${shortDate(dueDate)}: o mínimo foi pago, o resto rende juros"
        else -> "Fatura paga"
    }
}

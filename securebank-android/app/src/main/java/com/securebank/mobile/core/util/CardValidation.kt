package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.LimitUsage

/** Regras do cartão de débito (espelham securebank-web/src/lib/card.ts; o servidor confere tudo de novo). */
object CardValidation {
    const val MAX_OPEN_PER_ACCOUNT = 3
    const val MERCHANT_MAX = 60

    /** "9123456789012345" vira "9123 4567 8901 2345" (grupos de 4, como no plástico). */
    fun formatNumber(number: String): String = number.filter { it.isDigit() }.chunked(4).joinToString(" ")

    /** Validade como no cartão: mês com dois dígitos e ano com dois (07/31). */
    fun formatExpiry(month: Int, year: Int): String = "%02d/%02d".format(month, year % 100)

    fun statusLabel(status: String) = when (status) {
        "ACTIVE" -> "Ativo"
        "BLOCKED" -> "Bloqueado"
        "CANCELED" -> "Cancelado"
        else -> status
    }

    /** Estabelecimento: obrigatório, até 60 caracteres, sem quebra de linha nem controle nem marcas de direção. */
    fun merchantIssue(input: String): String? {
        val name = input.trim()
        if (name.isEmpty()) return "Informe o estabelecimento"
        if (name.length > MERCHANT_MAX) return "No máximo $MERCHANT_MAX caracteres"
        val bad = name.any {
            val type = Character.getType(it)
            it.isISOControl() || type == Character.LINE_SEPARATOR.toInt() || type == Character.PARAGRAPH_SEPARATOR.toInt() ||
                (type == Character.FORMAT.toInt() && it.code != 0x200D && it.code != 0x200C)
        }
        return if (bad) "Use só letras, números e símbolos comuns" else null
    }

    /** Valor da compra: positivo, dentro do saldo da conta e do limite do cartão; `null` quando está tudo certo. */
    fun amountIssue(input: String, balance: String?, limit: LimitUsage?): String? {
        val value = Money.parse(input) ?: return Validation.amountIssue(input)
        return (balance?.let { Validation.balanceIssue(value, it) }) ?: Validation.limitIssue(value, limit)
    }
}

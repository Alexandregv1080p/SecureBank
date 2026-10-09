package com.securebank.mobile.core.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Regras de câmbio que o app mostra antes de enviar (o servidor é quem vale e confere tudo de novo). */
object FxValidation {

    fun currencyName(code: String) = when (code) {
        "USD" -> "Dólar americano"
        "EUR" -> "Euro"
        else -> code
    }

    fun symbol(code: String) = when (code) {
        "USD" -> "US$"
        "EUR" -> "€"
        else -> code
    }

    /** "1.234,50" no formato da moeda estrangeira (mesma regra de milhar e centavos do real, com o símbolo dela). */
    fun format(code: String, amount: String): String = "${symbol(code)} ${Money.format(amount).removePrefix("R$").trim()}"

    /** Reais por unidade, como o servidor manda ("5.278000"), em texto curto: "5,278". */
    fun rateLabel(rate: String): String =
        rate.toBigDecimalOrNull()?.setScale(4, RoundingMode.HALF_UP)?.stripTrailingZeros()?.let {
            val text = if (it.scale() < 2) it.setScale(2) else it
            text.toPlainString().replace('.', ',')
        } ?: rate

    /** Custo estimado em reais de COMPRAR [amount]: arredonda para cima, como o servidor. */
    fun buyCost(amount: String, buyRate: String): String? = estimate(amount, buyRate, RoundingMode.UP)

    /** Valor estimado em reais ao VENDER [amount]: arredonda para baixo, como o servidor. */
    fun sellProceeds(amount: String, sellRate: String): String? = estimate(amount, sellRate, RoundingMode.DOWN)

    private fun estimate(amount: String, rate: String, mode: RoundingMode): String? {
        val a = Money.parse(amount)?.toBigDecimalOrNull() ?: return null
        val r = rate.toBigDecimalOrNull() ?: return null
        return a.multiply(r).setScale(2, mode).toPlainString()
    }

    /** Quantia positiva com até 2 casas. Na venda, também não pode passar do que há na carteira. */
    fun amountError(input: String, available: String? = null): String? {
        Validation.amountIssue(input)?.let { return it }
        val parsed = Money.parse(input) ?: return null
        val max = available?.toBigDecimalOrNull() ?: return null
        return if (parsed.toBigDecimal() > max) "Você tem só ${format("", available).trim()} na carteira" else null
    }

    fun sideLabel(side: String) = if (side == "BUY") "Compra" else "Venda"
}

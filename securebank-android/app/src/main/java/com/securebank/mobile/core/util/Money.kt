package com.securebank.mobile.core.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Dinheiro trafega como String ("1234.56") até a borda: só a exibição usa números formatados. */
object Money {
    private val noise = Regex("\\s|R\\$")
    private val valid = Regex("^\\d{1,13}(\\.\\d{1,2})?$")
    // 1.234 | 1.234,5 | 1.234.567,89: ponto separa milhar (grupos de 3, sem zero à esquerda), vírgula os centavos
    private val thousands = Regex("^[1-9]\\d{0,2}(\\.\\d{3})+(,\\d{1,2})?$")
    private val plain = Regex("^\\d+(,\\d{1,2})?$")
    // 1234.56 e 1.5: ponto com 1 ou 2 casas é decimal (é como a API devolve os valores)
    private val dotDecimal = Regex("^\\d+\\.\\d{1,2}$")

    /**
     * Converte o que o usuário digitou ("1.234,56", "1.500", "1234.56", "10") em "1234.56". No padrão brasileiro "1.500" é
     * mil e quinhentos, não 1,50. Devolve null se não for um valor positivo com no máximo 2 casas e 13 dígitos inteiros.
     */
    fun parse(input: String): String? {
        val text = input.replace(noise, "")
        if (text.isEmpty()) return null
        val normalized = when {
            thousands.matches(text) || plain.matches(text) -> text.replace(".", "").replace(',', '.')
            dotDecimal.matches(text) -> text
            else -> return null
        }
        if (!valid.matches(normalized)) return null
        return if (BigDecimal(normalized).signum() > 0) normalized else null
    }

    /** Soma exata (BigDecimal, nunca double) de valores em string; devolve "1234.56". */
    fun sum(values: List<String>): String =
        values.fold(BigDecimal.ZERO) { acc, v -> acc + BigDecimal(v) }.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

    /** Só para exibição (R$ 1.234,50). */
    fun format(amount: String): String =
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(amount))
}

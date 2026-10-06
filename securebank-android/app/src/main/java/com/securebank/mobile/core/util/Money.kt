package com.securebank.mobile.core.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Dinheiro trafega como String ("1234.56") até a borda: só a exibição usa números formatados. */
object Money {
    private val noise = Regex("\\s|R\\$")
    private val valid = Regex("^\\d{1,13}(\\.\\d{1,2})?$")

    /**
     * Converte o que o usuário digitou ("1.234,56", "1234.56", "10") em "1234.56".
     * Devolve null se não for um valor positivo com no máximo 2 casas.
     */
    fun parse(input: String): String? {
        val text = input.replace(noise, "")
        if (text.isEmpty()) return null
        val normalized = if (text.contains(',')) text.replace(".", "").replace(',', '.') else text
        if (!valid.matches(normalized)) return null
        return if (BigDecimal(normalized).signum() > 0) normalized else null
    }

    /** Só para exibição (R$ 1.234,50). */
    fun format(amount: String): String =
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal(amount))
}

package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.InvestmentProduct

object InvestmentValidation {

    /** Valor positivo com até 2 casas e pelo menos o mínimo do produto (o servidor confere de novo). */
    fun amountError(input: String, product: InvestmentProduct): String? {
        val parsed = Money.parse(input) ?: return OperationValidation.AMOUNT_MESSAGE
        val min = product.minAmount.toBigDecimalOrNull() ?: return null
        return if (parsed.toBigDecimal() < min) "O mínimo deste produto é ${Money.format(product.minAmount)}" else null
    }

    fun termLabel(termDays: Int?): String = if (termDays == null) "Liquidez diária" else "Prazo de $termDays dias"

    fun rateLabel(annualRatePercent: String): String = "${annualRatePercent.replace('.', ',')}% ao ano"
}

package com.securebank.mobile.core.util

enum class TransferField { Source, Branch, Number, Amount, Description }
enum class PaymentField { Account, Barcode, Amount, Description }

/** Mesmas regras dos formulários do web; o servidor valida de novo. */
object OperationValidation {
    const val AMOUNT_MESSAGE = "Informe um valor maior que zero, com até 2 casas decimais"
    private val branch = Regex("^\\d{4}$")
    private val accountNumber = Regex("^\\d{6,12}-\\d$")

    fun digits(value: String): String = value.filter { it.isDigit() }

    fun amount(input: String): String? = if (Money.parse(input) == null) AMOUNT_MESSAGE else null

    fun transfer(
        sourceId: String?,
        branch: String,
        number: String,
        amount: String,
        description: String,
    ): Map<TransferField, String> = buildMap {
        if (sourceId.isNullOrEmpty()) put(TransferField.Source, "Escolha a conta de origem")
        if (!this@OperationValidation.branch.matches(branch)) put(TransferField.Branch, "A agência tem 4 dígitos")
        if (!accountNumber.matches(number)) put(TransferField.Number, "Use o formato 123456-7")
        amount(amount)?.let { put(TransferField.Amount, it) }
        if (description.length > 140) put(TransferField.Description, "No máximo 140 caracteres")
    }

    fun payment(accountId: String?, barcode: String, amount: String, description: String): Map<PaymentField, String> = buildMap {
        if (accountId.isNullOrEmpty()) put(PaymentField.Account, "Escolha a conta")
        if (digits(barcode).length !in listOf(44, 47, 48)) put(PaymentField.Barcode, "O código tem 44, 47 ou 48 dígitos")
        amount(amount)?.let { put(PaymentField.Amount, it) }
        if (description.length > 140) put(PaymentField.Description, "No máximo 140 caracteres")
    }
}

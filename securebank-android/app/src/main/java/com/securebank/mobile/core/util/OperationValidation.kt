package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.LimitUsage

enum class TransferField { Source, Branch, Number, Amount, Description }
enum class PaymentField { Account, Barcode, Amount, Description }

/** Mesmas regras dos formulários do web; o servidor valida de novo. */
object OperationValidation {
    const val AMOUNT_MESSAGE = "Informe um valor maior que zero, com até 2 casas decimais"
    private val branch = Regex("^\\d{4}$")
    private val accountNumber = Regex("^\\d{6,12}-\\d$")

    fun digits(value: String): String = value.filter { it.isDigit() }

    fun amount(input: String): String? = Validation.amountIssue(input)

    /** Valor válido e, havendo conta e limite carregados, dentro do saldo e do limite (o servidor confere de novo). */
    fun amountFor(amount: String, source: Account?, limit: LimitUsage?): String? {
        Validation.amountIssue(amount)?.let { return it }
        val value = Money.parse(amount) ?: return null
        return source?.let { Validation.balanceIssue(value, it.balance.amount) } ?: Validation.limitIssue(value, limit)
    }

    fun transfer(
        sourceId: String?,
        branch: String,
        number: String,
        amount: String,
        description: String,
        source: Account? = null,
        limit: LimitUsage? = null,
    ): Map<TransferField, String> = buildMap {
        if (sourceId.isNullOrEmpty()) put(TransferField.Source, "Escolha a conta de origem")
        if (!this@OperationValidation.branch.matches(branch)) put(TransferField.Branch, "A agência tem 4 dígitos")
        when {
            !accountNumber.matches(number) -> put(TransferField.Number, "Use o formato 123456-0")
            !Validation.accountNumberValid(number) -> put(TransferField.Number, "Número de conta inválido: confira o dígito depois do hífen")
            source != null && Validation.sameAccount(source.branch, source.accountNumber, branch, number) ->
                put(TransferField.Number, "Escolha uma conta diferente da de origem")
        }
        val amountProblem = Validation.amountIssue(amount)
        if (amountProblem != null) {
            put(TransferField.Amount, amountProblem)
        } else {
            val value = Money.parse(amount)!!
            (source?.let { Validation.balanceIssue(value, it.balance.amount) } ?: Validation.limitIssue(value, limit))?.let { put(TransferField.Amount, it) }
        }
        if (description.length > 140) put(TransferField.Description, "No máximo 140 caracteres")
    }

    fun payment(
        accountId: String?,
        barcode: String,
        amount: String,
        description: String,
        source: Account? = null,
        limit: LimitUsage? = null,
    ): Map<PaymentField, String> = buildMap {
        if (accountId.isNullOrEmpty()) put(PaymentField.Account, "Escolha a conta")
        if (digits(barcode).length !in listOf(44, 47, 48)) put(PaymentField.Barcode, "O código tem 44, 47 ou 48 dígitos")
        amountFor(amount, source, limit)?.let { put(PaymentField.Amount, it) }
        if (description.length > 140) put(PaymentField.Description, "No máximo 140 caracteres")
    }
}

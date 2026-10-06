package com.securebank.mobile.core.util

enum class PixField { Key, Source, Amount, Message }

object PixValidation {
    const val MESSAGE_MAX = 140

    fun keyError(input: String): String? = if (input.isBlank()) "Informe a chave Pix ou cole o código" else null

    fun send(sourceId: String?, amount: String, message: String): Map<PixField, String> = buildMap {
        if (sourceId.isNullOrEmpty()) put(PixField.Source, "Escolha a conta")
        OperationValidation.amount(amount)?.let { put(PixField.Amount, it) }
        if (message.length > MESSAGE_MAX) put(PixField.Message, "No máximo $MESSAGE_MAX caracteres")
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

package com.securebank.mobile.core.util

enum class PiggyField { Account, Name, Goal }

/** Mesmas regras do servidor (nome 1 a 40 caracteres; meta opcional, positiva, até 2 casas). */
object PiggyValidation {
    const val NAME_MAX = 40

    fun validate(accountId: String?, name: String, goal: String): Map<PiggyField, String> = buildMap {
        if (accountId.isNullOrEmpty()) put(PiggyField.Account, "Escolha a conta")
        if (name.isBlank()) put(PiggyField.Name, "Dê um nome ao porquinho")
        else if (name.trim().length > NAME_MAX) put(PiggyField.Name, "No máximo $NAME_MAX caracteres")
        if (goal.isNotBlank()) Validation.amountIssue(goal)?.let { put(PiggyField.Goal, it) }
    }

    /** Meta digitada em "1.234,56" para "1234.56"; vazio = sem meta (null). Só chamar depois de [validate]. */
    fun goalOrNull(goal: String): String? = if (goal.isBlank()) null else Money.parse(goal)
}

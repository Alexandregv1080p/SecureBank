package com.securebank.mobile.core.util

enum class PasswordField { Current, Next, Confirm }

/** Mesmas regras da troca de senha do web (o servidor aplica a política completa). */
object PasswordValidation {
    fun validate(current: String, next: String, confirm: String): Map<PasswordField, String> = buildMap {
        if (current.isEmpty()) put(PasswordField.Current, "Informe a senha atual")
        Validation.passwordIssue(next)?.let { put(PasswordField.Next, it) }
        if (next != confirm) put(PasswordField.Confirm, "As senhas não conferem")
    }
}

package com.securebank.mobile.core.util

enum class PasswordField { Current, Next, Confirm }

/** Mesmas regras da troca de senha do web (o servidor aplica a política completa). */
object PasswordValidation {
    fun validate(current: String, next: String, confirm: String): Map<PasswordField, String> = buildMap {
        if (current.isEmpty()) put(PasswordField.Current, "Informe a senha atual")
        if (next.length < 12) put(PasswordField.Next, "Use ao menos 12 caracteres")
        else if (next.length > 128) put(PasswordField.Next, "Use no máximo 128 caracteres")
        if (next != confirm) put(PasswordField.Confirm, "As senhas não conferem")
    }
}

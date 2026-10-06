package com.securebank.mobile.core.util

enum class RegisterField { Name, Document, Email, Phone, Password, Confirm }

/** Mesmas regras do cadastro do web; o servidor valida de novo (isto só poupa uma ida e volta). */
object RegisterValidation {
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validate(
        name: String,
        document: String,
        email: String,
        phone: String,
        password: String,
        confirm: String,
    ): Map<RegisterField, String> = buildMap {
        if (name.trim().length < 2) put(RegisterField.Name, "Informe o nome completo")
        if (document.count { it.isDigit() } != 11) put(RegisterField.Document, "O CPF tem 11 dígitos")
        if (email.isBlank()) put(RegisterField.Email, "Informe o e-mail")
        else if (!this@RegisterValidation.email.matches(email.trim())) put(RegisterField.Email, "Informe um e-mail válido")
        if (Phone.toE164BR(phone) == null) put(RegisterField.Phone, "Informe DDD e número, ex.: 11 99999-8888")
        if (password.length < 12) put(RegisterField.Password, "Use ao menos 12 caracteres")
        else if (password.length > 128) put(RegisterField.Password, "Use no máximo 128 caracteres")
        if (password != confirm) put(RegisterField.Confirm, "As senhas não conferem")
    }
}

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
        Validation.fullNameIssue(name)?.let { put(RegisterField.Name, it) }
        if (document.count { it.isDigit() } != 11) put(RegisterField.Document, "O CPF tem 11 dígitos")
        else if (!Validation.cpfValid(document)) put(RegisterField.Document, "CPF inválido: confira os dígitos")
        if (email.isBlank()) put(RegisterField.Email, "Informe o e-mail")
        else if (!this@RegisterValidation.email.matches(email.trim())) put(RegisterField.Email, "Informe um e-mail válido")
        if (Phone.toE164BR(phone) == null) put(RegisterField.Phone, "Informe DDD e número, ex.: 11 99999-8888")
        Validation.passwordIssue(password, email)?.let { put(RegisterField.Password, it) }
        if (password != confirm) put(RegisterField.Confirm, "As senhas não conferem")
    }
}

package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.LimitUsage
import java.math.BigDecimal

/**
 * Regras de campo compartilhadas pelos formulários (espelham securebank-web/src/lib/validation.ts e o servidor).
 * São só para poupar a ida e volta e a biometria: o servidor confere tudo de novo.
 */
object Validation {

    // ---- conta ----

    /** Formato 123456-7 E dígito verificador (módulo 11, pesos 2..9 da direita), igual a AccountNumber.generate do servidor. */
    fun accountNumberValid(number: String): Boolean {
        if (!Regex("^\\d{6,12}-\\d$").matches(number)) return false
        val digits = number.substringBefore('-')
        var sum = 0
        var weight = 2
        for (i in digits.indices.reversed()) {
            sum += (digits[i] - '0') * weight
            weight = if (weight == 9) 2 else weight + 1
        }
        val remainder = sum % 11
        return (if (remainder < 2) 0 else 11 - remainder) == number.last() - '0'
    }

    /** Só os números: o hífen entra sozinho antes do último dígito (o verificador), então 1234560 vira 123456-0. */
    fun maskAccountNumber(raw: String): String {
        val d = raw.filter { it.isDigit() }.take(13)
        return if (d.length <= 6) d else d.dropLast(1) + "-" + d.last()
    }

    /** A conta de destino é a própria conta de origem? */
    fun sameAccount(sourceBranch: String, sourceNumber: String, branch: String, number: String): Boolean =
        sourceBranch == branch && sourceNumber == number

    // ---- valores ----

    /** Deixa digitar só um valor em reais: dígitos e uma vírgula, até 2 casas e 13 dígitos inteiros ("." vira vírgula, a menos que seja milhar). */
    fun sanitizeAmount(raw: String): String {
        var text = raw.filter { it.isDigit() || it == ',' || it == '.' }
        text = if (text.contains(',')) text.replace(".", "") else text.replaceFirst('.', ',').replace(".", "")
        val comma = text.indexOf(',')
        val integer = (if (comma >= 0) text.substring(0, comma) else text).trimStart('0').take(13).ifEmpty { if (comma >= 0 || text.isNotEmpty()) "0" else "" }
        if (comma < 0) return integer
        return integer + "," + text.substring(comma + 1).filter { it.isDigit() }.take(2)
    }

    /** Por que o valor digitado não serve (mensagem específica), ou null se é um valor positivo com até 2 casas. */
    fun amountIssue(input: String): String? {
        val text = input.replace(Regex("\\s|R\\$"), "")
        if (text.isEmpty()) return "Informe o valor"
        if (Money.parse(input) != null) return null
        val normalized = if (text.contains(',')) text.replace(".", "").replace(',', '.') else text
        return when {
            !Regex("^\\d+(\\.\\d*)?$").matches(normalized) -> "Use só números e vírgula para os centavos, ex.: 1250,50"
            normalized.substringAfter('.', "").length > 2 -> "Use no máximo 2 casas decimais"
            normalized.substringBefore('.').length > 13 -> "Esse valor é alto demais"
            else -> "O valor deve ser maior que zero"
        }
    }

    /** [amount] já normalizado ("1234.56"). */
    fun balanceIssue(amount: String, balance: String): String? {
        val a = amount.toBigDecimalOrNull() ?: return null
        val b = balance.toBigDecimalOrNull() ?: return null
        return if (a > b) "Saldo insuficiente: você tem ${Money.format(b.toPlainString())} nesta conta" else null
    }

    /** Limite por operação e o que resta hoje ([amount] normalizado). Sem limite carregado, não opina (o servidor decide). */
    fun limitIssue(amount: String, usage: LimitUsage?): String? {
        usage ?: return null
        val a = amount.toBigDecimalOrNull() ?: return null
        if (a > BigDecimal(usage.perOperation.amount)) return "Acima do limite por operação de ${Money.format(usage.perOperation.amount)}"
        if (a > BigDecimal(usage.remainingToday.amount)) return "Acima do que resta do limite de hoje (${Money.format(usage.remainingToday.amount)})"
        return null
    }

    // ---- cadastro ----

    fun cpfValid(input: String): Boolean {
        val d = input.filter { it.isDigit() }
        if (d.length != 11 || d.toSet().size == 1) return false
        fun check(length: Int): Int {
            val sum = (0 until length).sumOf { (d[it] - '0') * (length + 1 - it) }
            val r = sum * 10 % 11
            return if (r == 10) 0 else r
        }
        return check(9) == d[9] - '0' && check(10) == d[10] - '0'
    }

    fun maskCpf(raw: String): String {
        val d = raw.filter { it.isDigit() }.take(11)
        return buildString {
            d.forEachIndexed { i, c ->
                if (i == 3 || i == 6) append('.')
                if (i == 9) append('-')
                append(c)
            }
        }
    }

    /** (11) 99999-8888 ou (11) 9999-8888, conforme os dígitos; aceita colar com +55. */
    fun maskPhone(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.length > 11 && d.startsWith("55")) d = d.drop(2)
        d = d.take(11)
        if (d.isEmpty()) return ""
        val ddd = d.take(2)
        val rest = d.drop(2)
        val body = when {
            rest.length <= 4 -> rest
            rest.length <= 8 -> rest.take(4) + "-" + rest.drop(4)
            else -> rest.take(5) + "-" + rest.drop(5)
        }
        return if (ddd.length < 2) "($ddd" else "($ddd) $body".trimEnd()
    }

    fun fullNameIssue(name: String): String? {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.size < 2 -> "Informe nome e sobrenome"
            name.length > 120 -> "Use no máximo 120 caracteres"
            !words.all { w -> w.all { it.isLetter() || it == '\'' || it == '-' || it == '.' } } -> "Use só letras no nome"
            else -> null
        }
    }

    private val commonPasswords = setOf(
        "password1234", "123456789012", "qwertyuiop12", "1234567890ab", "senha1234567", "senhasenha123", "passwordpassword",
        "letmein12345", "iloveyou1234", "admin1234567", "welcome12345", "changeme1234", "abcdefghijkl", "111111111111", "000000000000",
    )

    /** Mesma política do servidor (PasswordPolicy): tamanho, não ser previsível e não conter o e-mail. */
    fun passwordIssue(password: String, email: String? = null): String? {
        if (password.length < 12 || password.length > 128) return "Use de 12 a 128 caracteres"
        val lower = password.lowercase()
        if (lower in commonPasswords || lower.toSet().size < 5) return "Essa senha é fácil de adivinhar"
        val local = email?.trim()?.lowercase()?.substringBefore('@').orEmpty()
        if (local.length >= 4 && lower.contains(local)) return "A senha não pode conter o seu e-mail"
        return null
    }
}

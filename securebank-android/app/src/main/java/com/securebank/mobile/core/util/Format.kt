package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.Transaction
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Textos de exibição (pt-BR), os mesmos do web. */
object Format {
    private val locale: Locale = Locale.forLanguageTag("pt-BR")
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM, HH:mm", locale)
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", locale)

    fun accountTypeLabel(type: String) = if (type == "CHECKING") "Conta corrente" else "Poupança"

    fun accountLabel(branch: String, accountNumber: String) = "Ag. $branch, conta $accountNumber"

    fun limitLabel(type: String) = when (type) {
        "WITHDRAW" -> "Saque"
        "TRANSFER" -> "Transferência"
        "PAYMENT" -> "Pagamento"
        "PIX" -> "Pix"
        else -> type
    }

    /** Categorias do extrato (derivadas do tipo no servidor). */
    val categories = listOf("CASH", "TRANSFERS", "PAYMENTS", "PIX", "SAVINGS")

    fun categoryLabel(category: String) = when (category) {
        "CASH" -> "Depósitos e saques"
        "TRANSFERS" -> "Transferências"
        "PAYMENTS" -> "Pagamentos"
        "PIX" -> "Pix"
        "SAVINGS" -> "Porquinhos"
        else -> category
    }

    fun month(m: YearMonth): String = month.format(m)

    fun describe(t: Transaction): String = when (t.type) {
        "DEPOSIT" -> "Depósito"
        "WITHDRAW" -> "Saque"
        "TRANSFER" -> if (t.direction == "CREDIT") "Transferência recebida" else "Transferência enviada"
        "PAYMENT" -> "Pagamento"
        "PIX_OUT" -> "Pix enviado"
        "PIX_IN" -> "Pix recebido"
        "PIX_RETURN_OUT" -> "Devolução de Pix enviada"
        "PIX_RETURN_IN" -> "Devolução de Pix recebida"
        "PIGGY_IN" -> "Guardado no porquinho"
        "PIGGY_OUT" -> "Resgate do porquinho"
        else -> "Estorno"
    }

    /** ISO-8601 em UTC (como a API devolve) para o fuso do aparelho. */
    fun dateTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(Instant.parse(iso).atZone(zone))

    fun date(day: java.time.LocalDate): String = date.format(day)

    /** User-Agent da sessão em linguagem simples (a lista de dispositivos mostra o que o servidor guardou). */
    fun device(userAgent: String?): String {
        val ua = userAgent?.trim().orEmpty()
        return when {
            ua.isEmpty() -> "Dispositivo desconhecido"
            ua.startsWith("SecureBank-Android/") -> "App SecureBank Android ${ua.substringAfter('/')}"
            ua.contains("Firefox/") -> "Navegador (Firefox)"
            ua.contains("Edg/") -> "Navegador (Edge)"
            ua.contains("Chrome/") -> "Navegador (Chrome)"
            ua.contains("Safari/") -> "Navegador (Safari)"
            else -> ua.take(60)
        }
    }

    fun firstName(fullName: String): String = fullName.trim().substringBefore(' ')
}

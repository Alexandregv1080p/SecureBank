package com.securebank.mobile.core.util

object Phone {
    /** DDDs que existem no Brasil. */
    private val ddds = (
        "11 12 13 14 15 16 17 18 19 21 22 24 27 28 31 32 33 34 35 37 38 41 42 43 44 45 46 47 48 49 51 53 54 55 " +
            "61 62 63 64 65 66 67 68 69 71 73 74 75 77 79 81 82 83 84 85 86 87 88 89 91 92 93 94 95 96 97 98 99"
        ).split(" ").toSet()

    /** DDD + número sem o 55 do país: 10 (fixo) ou 11 dígitos (celular). */
    private fun national(input: String): String {
        val digits = input.filter { it.isDigit() }
        return if (digits.startsWith("55") && digits.length >= 12) digits.drop(2) else digits
    }

    /** Por que o número não serve (mensagem específica), ou null. Celular tem 9 dígitos e começa com 9; fixo tem 8 e começa com 2 a 5. */
    fun issue(input: String): String? {
        val n = national(input)
        return when {
            n.length !in 10..11 -> "Informe DDD e número, ex.: 11 99999-8888"
            n.take(2) !in ddds -> "DDD inválido. Confira os dois primeiros dígitos"
            n.length == 11 && n[2] != '9' -> "Celular com 9 dígitos começa com 9, ex.: 11 99999-8888"
            n.length == 10 && n[2] !in '2'..'5' -> "Fixo começa com 2 a 5; para celular, coloque o 9 na frente"
            else -> null
        }
    }

    /** "11 99999-8888" ou "+55 11 99999-8888" para E.164 (+5511999998888); null se não for um número brasileiro válido. */
    fun toE164BR(input: String): String? = if (issue(input) == null) "+55${national(input)}" else null
}

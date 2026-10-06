package com.securebank.mobile.core.util

object Phone {
    /** "11 99999-8888" ou "+55 11 99999-8888" para E.164 (+5511999998888); null se não parecer um número brasileiro. */
    fun toE164BR(input: String): String? {
        val digits = input.filter { it.isDigit() }
        val national = if (digits.startsWith("55") && digits.length >= 12) digits.drop(2) else digits
        return if (national.length in 10..11) "+55$national" else null
    }
}

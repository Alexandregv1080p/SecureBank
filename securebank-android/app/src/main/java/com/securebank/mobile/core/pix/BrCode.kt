package com.securebank.mobile.core.pix

import java.text.Normalizer

/**
 * "Pix Copia e Cola" estático (BR Code, EMV QRCPS): texto em blocos id(2) + tamanho(2) + valor, fechado por um CRC16.
 * É o mesmo texto que vira o QR code. Gerar e ler acontece só no aparelho; o servidor não participa.
 * QR dinâmico (cobrança com URL) não é suportado.
 */
object BrCode {

    class InvalidBrCodeException(message: String) : Exception(message)

    data class Data(val key: String, val amount: String?, val name: String, val city: String, val txid: String)

    private const val GUI = "br.gov.bcb.pix"

    /** Texto do QR para receber na [key]; [amount] (ex.: "10.50") é opcional (sem valor, quem paga digita). */
    fun encode(key: String, name: String, city: String = "SAO PAULO", amount: String? = null, txid: String = "***"): String {
        require(key.isNotBlank() && key.length <= 77) { "chave Pix inválida" }
        val merchant = tlv("00", GUI) + tlv("01", key)
        val body = tlv("00", "01") + tlv("01", "11") + tlv("26", merchant) + tlv("52", "0000") + tlv("53", "986") +
            (amount?.let { tlv("54", it) } ?: "") + tlv("58", "BR") + tlv("59", clean(name, 25).ifEmpty { "RECEBEDOR" }) +
            tlv("60", clean(city, 15).ifEmpty { "BRASIL" }) + tlv("62", tlv("05", txid.ifEmpty { "***" }))
        val withCrcHeader = body + "6304"
        return withCrcHeader + crc16(withCrcHeader)
    }

    /** Lê um código colado. Confere o CRC (um caractere errado é pego aqui, antes de qualquer consulta). */
    fun decode(code: String): Data {
        val text = code.trim()
        if (text.length < 12 || !text.startsWith("000201")) throw InvalidBrCodeException("Não parece um código Pix.")
        if (text.substring(text.length - 8, text.length - 4) != "6304") throw InvalidBrCodeException("Código Pix incompleto.")
        val expected = crc16(text.substring(0, text.length - 4))
        if (!text.takeLast(4).equals(expected, ignoreCase = true)) throw InvalidBrCodeException("Código Pix corrompido. Copie de novo.")

        val fields = parse(text.substring(0, text.length - 8))
        val merchant = parse(fields["26"] ?: throw InvalidBrCodeException("Não é um código Pix."))
        if (!merchant["00"].equals(GUI, ignoreCase = true)) throw InvalidBrCodeException("Não é um código Pix.")
        val key = merchant["01"]
            ?: throw InvalidBrCodeException(
                if (merchant.containsKey("25")) "Pix de cobrança (QR dinâmico) ainda não é suportado. Use a chave." else "O código não traz uma chave Pix.",
            )
        return Data(
            key = key,
            amount = fields["54"],
            name = fields["59"].orEmpty(),
            city = fields["60"].orEmpty(),
            txid = parse(fields["62"].orEmpty())["05"] ?: "***",
        )
    }

    /** Parece um copia-e-cola (e não uma chave)? Todo BR Code começa pelo formato "000201". */
    fun looksLikeCode(input: String): Boolean = input.trim().startsWith("000201")

    /** CRC-16/CCITT-FALSE (polinômio 0x1021, início 0xFFFF), em 4 hexadecimais maiúsculos. */
    fun crc16(input: String): String {
        var crc = 0xFFFF
        for (byte in input.toByteArray(Charsets.US_ASCII)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return "%04X".format(crc)
    }

    private fun tlv(id: String, value: String): String {
        require(value.length <= 99) { "campo $id grande demais" }
        return id + value.length.toString().padStart(2, '0') + value
    }

    private fun parse(text: String): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        var i = 0
        while (i < text.length) {
            if (i + 4 > text.length) throw InvalidBrCodeException("Código Pix incompleto.")
            val id = text.substring(i, i + 2)
            val length = text.substring(i + 2, i + 4).toIntOrNull() ?: throw InvalidBrCodeException("Código Pix inválido.")
            if (i + 4 + length > text.length) throw InvalidBrCodeException("Código Pix incompleto.")
            fields[id] = text.substring(i + 4, i + 4 + length)
            i += 4 + length
        }
        return fields
    }

    /** Nome e cidade do BR Code: maiúsculas, sem acento e só A-Z/0-9/espaço. */
    private fun clean(text: String, max: Int): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .filter { it.code < 128 }
            .uppercase()
            .filter { it.isLetterOrDigit() || it == ' ' }
            .trim()
            .take(max)
}

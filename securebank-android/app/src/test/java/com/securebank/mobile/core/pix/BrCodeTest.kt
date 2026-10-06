package com.securebank.mobile.core.pix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrCodeTest {
    @Test
    fun crcMatchesTheStandardCheckValue() {
        // vetor de teste conhecido do CRC-16/CCITT-FALSE
        assertEquals("29B1", BrCode.crc16("123456789"))
    }

    @Test
    fun encodedCodeCarriesTheKeyAndAValidCrc() {
        val code = BrCode.encode("ana@example.com", "Ana Souza", amount = "10.50")

        assertTrue(code.startsWith("000201"))
        assertTrue(code.contains("br.gov.bcb.pix"))
        assertTrue(code.contains("ana@example.com"))
        assertTrue(code.contains("540510.50")) // campo 54 (valor), tamanho 05
        assertEquals(BrCode.crc16(code.dropLast(4)), code.takeLast(4)) // o CRC fecha o texto
    }

    @Test
    fun decodeReadsBackWhatEncodeWrote() {
        val code = BrCode.encode("123e4567-e89b-12d3-a456-426614174000", "Ana Souza", "São Paulo", "99.90", "TX123")

        val data = BrCode.decode(code)

        assertEquals("123e4567-e89b-12d3-a456-426614174000", data.key)
        assertEquals("99.90", data.amount)
        assertEquals("ANA SOUZA", data.name) // sem acento e em maiúsculas
        assertEquals("SAO PAULO", data.city)
        assertEquals("TX123", data.txid)
    }

    @Test
    fun theAmountIsOptional() {
        val data = BrCode.decode(BrCode.encode("+5511999998888", "Ana"))
        assertNull(data.amount)
        assertEquals("***", data.txid)
        assertEquals("+5511999998888", data.key)
    }

    @Test
    fun aSingleWrongCharacterIsCaughtByTheCrc() {
        val code = BrCode.encode("ana@example.com", "Ana Souza", amount = "10.50")
        val tampered = code.replace("540510.50", "540510.60")

        val e = assertThrows(BrCode.InvalidBrCodeException::class.java) { BrCode.decode(tampered) }

        assertTrue(e.message!!.contains("corrompido"))
    }

    @Test
    fun codesPastedWithSpacesOrLowercaseCrcStillWork() {
        val code = BrCode.encode("ana@example.com", "Ana")
        val lowerCrc = code.dropLast(4) + code.takeLast(4).lowercase()

        assertEquals("ana@example.com", BrCode.decode("  $lowerCrc \n").key)
    }

    @Test
    fun garbageAndIncompleteCodesAreRejectedWithAFriendlyMessage() {
        for (bad in listOf("", "abc", "00020126", "ana@example.com", BrCode.encode("a@b.co", "A").dropLast(3))) {
            assertThrows("deveria recusar '$bad'", BrCode.InvalidBrCodeException::class.java) { BrCode.decode(bad) }
        }
    }

    @Test
    fun aCodeThatIsNotPixIsRefused() {
        // CRC correto, mas o identificador do arranjo não é br.gov.bcb.pix
        val body = "00020126" + "30" + "0010outro.com01" + "04" + "abcd" + "5204000053039865802BR5903ANA6003SAO" + "6304"
        val code = body + BrCode.crc16(body)

        assertThrows(BrCode.InvalidBrCodeException::class.java) { BrCode.decode(code) }
    }

    @Test
    fun dynamicQrCodesAreExplainedNotMisread() {
        val merchant = "0014br.gov.bcb.pix" + "2542pix.exemplo.com/qr/v2/abcdefghijklmnopqrst"
        val body = "000201" + "26" + merchant.length.toString().padStart(2, '0') + merchant + "5204000053039865802BR5903ANA6003SAO" + "6304"
        val code = body + BrCode.crc16(body)

        val e = assertThrows(BrCode.InvalidBrCodeException::class.java) { BrCode.decode(code) }

        assertTrue(e.message!!.contains("dinâmico"))
    }

    @Test
    fun looksLikeCodeTellsAKeyFromAPastedCode() {
        assertTrue(BrCode.looksLikeCode(" " + BrCode.encode("a@b.co", "A")))
        assertFalse(BrCode.looksLikeCode("ana@example.com"))
        assertFalse(BrCode.looksLikeCode("52998224725"))
    }

    @Test
    fun nameAndCityAreCleanedAndTruncated() {
        val data = BrCode.decode(BrCode.encode("a@b.co", "José Antônio da Silva Pereira de Albuquerque", "São José dos Campos do Norte"))

        assertEquals("JOSE ANTONIO DA SILVA PE", data.name.take(24))
        assertTrue(data.name.length <= 25)
        assertTrue(data.city.length <= 15)
    }

    @Test
    fun blankNameFallsBackToAPlaceholder() {
        assertEquals("RECEBEDOR", BrCode.decode(BrCode.encode("a@b.co", "   ")).name)
    }
}

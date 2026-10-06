package com.securebank.mobile.core.session

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JWT sintético, sem assinatura válida: o app só lê os claims para a interface. */
fun fakeJwt(sub: String = "user-1", sid: String = "sid-1", cid: String? = "cust-1", exp: Long = 4_102_444_800L): String {
    fun b64(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
    val cidPart = if (cid != null) ",\"cid\":\"$cid\"" else ""
    return b64("""{"alg":"RS256"}""") + "." + b64("""{"sub":"$sub","sid":"$sid","exp":$exp$cidPart,"roles":["CUSTOMER"],"extra":1}""") + ".sig"
}

class ClaimsTest {
    @Test
    fun decodesTheFieldsTheUiNeedsAndIgnoresTheRest() {
        val claims = Claims.decode(fakeJwt())
        assertEquals("user-1", claims.sub)
        assertEquals("sid-1", claims.sid)
        assertEquals(listOf("CUSTOMER"), claims.roles)
        assertTrue(claims.isCustomer)
    }

    @Test
    fun staffTokensHaveNoCustomer() {
        assertFalse(Claims.decode(fakeJwt(cid = null)).isCustomer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbageIsRejected() {
        Claims.decode("not-a-jwt")
    }
}

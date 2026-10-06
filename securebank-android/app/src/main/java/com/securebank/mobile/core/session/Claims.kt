package com.securebank.mobile.core.session

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lê os claims só para a interface (papel, expiração). Quem valida o token é sempre o servidor. */
@Serializable
data class Claims(
    val sub: String,
    val sid: String,
    val exp: Long,
    val cid: String? = null,
    val roles: List<String> = emptyList(),
) {
    /** Usuário da equipe (sem cliente): o app é de clientes. */
    val isCustomer: Boolean get() = cid != null

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(token: String): Claims {
            val payload = token.split('.').getOrNull(1) ?: throw IllegalArgumentException("not a JWT")
            return json.decodeFromString(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
        }
    }
}

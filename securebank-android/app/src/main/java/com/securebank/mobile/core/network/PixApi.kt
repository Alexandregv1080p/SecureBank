package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Chave Pix. O VALOR vem do cadastro do cliente (o servidor o define): o app só escolhe o tipo. */
@Serializable
data class PixKey(val id: String, val accountId: String, val type: String, val key: String, val createdAt: String)

@Serializable
data class RegisterPixKeyRequest(val accountId: String, val type: String)

/** O que a tela de confirmação mostra antes de enviar: só nome e CPF MASCARADOS. */
@Serializable
data class PixLookup(
    val type: String,
    val key: String,
    val name: String,
    val document: String,
    val bank: String = "SecureBank",
    val ownAccount: Boolean = false,
)

@Serializable
data class SendPixRequest(val sourceAccountId: String, val key: String, val amount: String, val message: String? = null)

/** Um Pix enviado ou recebido; [direction] é SENT ou RECEIVED do ponto de vista de quem consulta. */
@Serializable
data class PixEntry(
    val id: String,
    val endToEndId: String,
    val sourceAccountId: String,
    val amount: Money,
    val message: String? = null,
    val key: String,
    val counterpartName: String,
    val direction: String,
    val createdAt: String,
) {
    val sent: Boolean get() = direction == "SENT"
}

interface PixApi {
    @GET("pix/keys") suspend fun keys(): List<PixKey>
    @POST("pix/keys") suspend fun registerKey(@Body body: RegisterPixKeyRequest): PixKey
    @DELETE("pix/keys/{id}") suspend fun deleteKey(@Path("id") id: String)
    @GET("pix/keys/lookup") suspend fun lookup(@Query("key") key: String): PixLookup

    @POST("pix/transfers")
    suspend fun send(@Body body: SendPixRequest, @Header("Idempotency-Key") key: String): PixEntry

    @GET("pix/transfers")
    suspend fun history(@Query("page") page: Int = 0, @Query("size") size: Int = 20): Page<PixEntry>
}

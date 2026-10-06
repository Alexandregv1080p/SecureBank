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
    /** Preenchido quando este Pix é a devolução de outro. */
    val refundOfId: String? = null,
    val refundedAmount: Money? = null,
    /** Quanto ainda dá para devolver (só para quem RECEBEU, em até 90 dias). */
    val refundableAmount: Money? = null,
    val createdAt: String,
) {
    val sent: Boolean get() = direction == "SENT"
    val isRefund: Boolean get() = refundOfId != null
    val canRefund: Boolean get() = !sent && !isRefund && (refundableAmount?.amount?.toBigDecimalOrNull()?.signum() ?: 0) > 0
}

@Serializable
data class RefundRequest(val amount: String? = null)

/** Cobrança que EU criei (QR dinâmico): valor fixo, validade, uso único. [location] vai dentro do QR. */
@Serializable
data class PixCharge(
    val txid: String,
    val accountId: String,
    val amount: Money,
    val description: String? = null,
    val status: String,
    val expiresAt: String,
    val createdAt: String,
    val paidAt: String? = null,
    val location: String,
)

/** O que o PAGADOR vê ao ler o QR de uma cobrança: nome e CPF mascarados. */
@Serializable
data class PixChargeView(
    val txid: String,
    val amount: Money,
    val description: String? = null,
    val status: String,
    val expiresAt: String,
    val receiverName: String,
    val receiverDocument: String,
    val own: Boolean = false,
    val bank: String = "SecureBank",
)

@Serializable
data class CreateChargeRequest(val accountId: String, val amount: String, val description: String? = null, val expiresInMinutes: Int? = null)

@Serializable
data class PayChargeRequest(val sourceAccountId: String)

/** Pix agendado: a autorização é dada ao agendar; o dinheiro só se move na data. */
@Serializable
data class PixScheduleDto(
    val id: String,
    val sourceAccountId: String,
    val key: String,
    val destinationName: String,
    val amount: Money,
    val message: String? = null,
    val scheduledFor: String,
    val status: String,
    val failureReason: String? = null,
    val executedPixId: String? = null,
    val createdAt: String,
)

@Serializable
data class CreateScheduleRequest(val sourceAccountId: String, val key: String, val amount: String, val message: String? = null, val scheduledFor: String)

interface PixApi {
    @GET("pix/keys") suspend fun keys(): List<PixKey>
    @POST("pix/keys") suspend fun registerKey(@Body body: RegisterPixKeyRequest): PixKey
    @DELETE("pix/keys/{id}") suspend fun deleteKey(@Path("id") id: String)
    @GET("pix/keys/lookup") suspend fun lookup(@Query("key") key: String): PixLookup

    @POST("pix/transfers")
    suspend fun send(@Body body: SendPixRequest, @Header("Idempotency-Key") key: String): PixEntry

    @GET("pix/transfers")
    suspend fun history(@Query("page") page: Int = 0, @Query("size") size: Int = 20): Page<PixEntry>

    @GET("pix/transfers/{id}") suspend fun transfer(@Path("id") id: String): PixEntry

    @POST("pix/transfers/{id}/refund")
    suspend fun refund(@Path("id") id: String, @Body body: RefundRequest, @Header("Idempotency-Key") key: String): PixEntry

    // ---- cobranças (QR dinâmico)
    @POST("pix/charges") suspend fun createCharge(@Body body: CreateChargeRequest): PixCharge
    @GET("pix/charges") suspend fun charges(@Query("page") page: Int = 0, @Query("size") size: Int = 20): Page<PixCharge>
    @GET("pix/charges/{txid}") suspend fun charge(@Path("txid") txid: String): PixChargeView
    @DELETE("pix/charges/{txid}") suspend fun cancelCharge(@Path("txid") txid: String)

    @POST("pix/charges/{txid}/pay")
    suspend fun payCharge(@Path("txid") txid: String, @Body body: PayChargeRequest, @Header("Idempotency-Key") key: String): PixEntry

    // ---- agendados
    @POST("pix/schedules")
    suspend fun schedule(@Body body: CreateScheduleRequest, @Header("Idempotency-Key") key: String): PixScheduleDto

    @GET("pix/schedules")
    suspend fun schedules(@Query("page") page: Int = 0, @Query("size") size: Int = 20): Page<PixScheduleDto>

    @DELETE("pix/schedules/{id}") suspend fun cancelSchedule(@Path("id") id: String)
}

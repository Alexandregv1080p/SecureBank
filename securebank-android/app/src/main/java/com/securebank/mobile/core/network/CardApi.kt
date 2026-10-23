package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Cartão de débito virtual: a listagem só traz os 4 últimos dígitos; o número inteiro vem de /reveal. */
@Serializable
data class Card(
    val id: String,
    val accountId: String,
    val holderName: String,
    val maskedNumber: String,
    val last4: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val status: String,
    /** Por que foi bloqueado ou cancelado, nas palavras do cliente. */
    val statusReason: String? = null,
    val createdAt: String,
)

@Serializable
data class CardDetails(val number: String, val expiryMonth: Int, val expiryYear: Int, val cvv: String, val holderName: String)

@Serializable
data class CardPurchase(val id: String, val cardId: String, val amount: Money, val merchant: String, val transactionId: String, val createdAt: String)

@Serializable
data class CardPurchasePage(val items: List<CardPurchase>, val page: Int, val size: Int, val totalElements: Long)

@Serializable
data class IssueCardRequest(val accountId: String)

/** Bloquear e cancelar pedem o motivo (de 3 a 140 caracteres): fica no cartão e na auditoria. */
@Serializable
data class CardReasonRequest(val reason: String)

@Serializable
data class CardPurchaseRequest(val amount: String, val merchant: String)

interface CardApi {
    @GET("cards") suspend fun list(): List<Card>
    @GET("cards/{id}") suspend fun get(@Path("id") id: String): Card
    @POST("cards") suspend fun issue(@Body body: IssueCardRequest): Card
    @POST("cards/{id}/reveal") suspend fun reveal(@Path("id") id: String): CardDetails
    @POST("cards/{id}/block") suspend fun block(@Path("id") id: String, @Body body: CardReasonRequest): Card
    @POST("cards/{id}/unblock") suspend fun unblock(@Path("id") id: String): Card
    @POST("cards/{id}/cancel") suspend fun cancel(@Path("id") id: String, @Body body: CardReasonRequest): Card

    @POST("cards/{id}/purchases")
    suspend fun purchase(@Path("id") id: String, @Body body: CardPurchaseRequest, @Header("Idempotency-Key") key: String): CardPurchase

    @GET("cards/{id}/purchases")
    suspend fun purchases(@Path("id") id: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): CardPurchasePage
}

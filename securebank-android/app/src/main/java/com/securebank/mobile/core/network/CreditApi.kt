package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Fatura de um mês (AAAA-MM): vence no dia escolhido e fecha 7 dias antes. [status]: OPEN, CLOSED, OVERDUE ou PAID. */
@Serializable
data class Invoice(
    val month: String,
    val closingDate: String,
    val dueDate: String,
    val total: Money,
    val paid: Money,
    val remaining: Money,
    val status: String,
)

@Serializable
data class CreditSummary(
    val cardId: String,
    val creditLimit: Money,
    val used: Money,
    val available: Money,
    val dueDay: Int,
    val overdue: Boolean,
    val currentInvoice: Invoice,
)

@Serializable
data class InvoiceItem(val merchant: String, val number: Int, val installments: Int, val amount: Money, val purchasedOn: String)

@Serializable
data class InvoicePayment(val amount: Money, val createdAt: String)

@Serializable
data class InvoiceDetail(val invoice: Invoice, val items: List<InvoiceItem>, val payments: List<InvoicePayment>)

@Serializable
data class CreditPurchase(
    val id: String,
    val cardId: String,
    val merchant: String,
    val total: Money,
    val installments: Int,
    val installmentAmount: Money,
    val createdAt: String,
)

@Serializable
data class CreditPurchasePage(val items: List<CreditPurchase>, val page: Int, val size: Int, val totalElements: Long)

@Serializable
data class IssueCreditRequest(val accountId: String, val dueDay: Int)

@Serializable
data class CreditPurchaseRequest(val amount: String, val merchant: String, val installments: Int)

@Serializable
data class PayInvoiceRequest(val amount: String)

interface CreditApi {
    @POST("credit/cards") suspend fun issue(@Body body: IssueCreditRequest): Card
    @GET("credit/cards/{id}") suspend fun summary(@Path("id") id: String): CreditSummary
    @GET("credit/cards/{id}/invoices") suspend fun invoices(@Path("id") id: String): List<Invoice>
    @GET("credit/cards/{id}/invoices/{month}") suspend fun invoice(@Path("id") id: String, @Path("month") month: String): InvoiceDetail

    @POST("credit/cards/{id}/purchases")
    suspend fun purchase(@Path("id") id: String, @Body body: CreditPurchaseRequest, @Header("Idempotency-Key") key: String): CreditPurchase

    @GET("credit/cards/{id}/purchases")
    suspend fun purchases(@Path("id") id: String, @Query("page") page: Int = 0, @Query("size") size: Int = 20): CreditPurchasePage

    @POST("credit/cards/{id}/invoices/{month}/payments")
    suspend fun payInvoice(@Path("id") id: String, @Path("month") month: String, @Body body: PayInvoiceRequest, @Header("Idempotency-Key") key: String): Invoice
}

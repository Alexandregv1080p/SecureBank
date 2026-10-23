package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Fatura de um mês (AAAA-MM): vence no dia escolhido e fecha 7 dias antes. [status]: OPEN, CLOSED, OVERDUE, REVOLVING (mínimo pago, resto rende juros) ou PAID. */
@Serializable
data class Invoice(
    val month: String,
    val closingDate: String,
    val dueDate: String,
    val principal: Money,
    /** Multa e juros por atraso. */
    val charges: Money,
    val total: Money,
    val paid: Money,
    val remaining: Money,
    /** Quanto falta pagar para cumprir o pagamento mínimo. */
    val minimumDue: Money,
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
    /** Num cartão adicional: o principal, cujo limite e faturas ele usa. */
    val mainCardId: String? = null,
)

@Serializable
data class InvoiceItem(val merchant: String, val number: Int, val installments: Int, val amount: Money, val purchasedOn: String)

@Serializable
data class InvoicePayment(val amount: Money, val createdAt: String, val kind: String = "CASH")

@Serializable
data class InvoiceCharge(val kind: String, val amount: Money)

@Serializable
data class InvoiceDetail(
    val invoice: Invoice,
    val items: List<InvoiceItem>,
    val charges: List<InvoiceCharge> = emptyList(),
    val payments: List<InvoicePayment>,
)

/** Uma opção de parcelamento da fatura; [monthlyRate] em %. */
@Serializable
data class RefinancePlan(val installments: Int, val installment: Money, val total: Money, val interest: Money, val monthlyRate: String)

@Serializable
data class RefinanceOffer(val invoice: Invoice, val options: List<RefinancePlan>)

@Serializable
data class Refinanced(val invoice: Invoice, val purchase: CreditPurchase, val plan: RefinancePlan)

@Serializable
data class RefinanceRequest(val installments: Int)

@Serializable
data class LimitRequest(val limit: String)

@Serializable
data class AdditionalCardRequest(val holderName: String)

@Serializable
data class CreditPurchase(
    val id: String,
    val cardId: String,
    /** O cartão adicional que fez a compra (nulo = o principal). */
    val madeWith: String? = null,
    val kind: String = "PURCHASE",
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
    @POST("credit/cards/{id}/additional") suspend fun additional(@Path("id") id: String, @Body body: AdditionalCardRequest): Card
    @PUT("credit/cards/{id}/limit") suspend fun changeLimit(@Path("id") id: String, @Body body: LimitRequest): CreditSummary
    @GET("credit/cards/{id}/invoices/{month}/refinance") suspend fun refinanceOptions(@Path("id") id: String, @Path("month") month: String): RefinanceOffer

    @POST("credit/cards/{id}/invoices/{month}/refinance")
    suspend fun refinance(@Path("id") id: String, @Path("month") month: String, @Body body: RefinanceRequest, @Header("Idempotency-Key") key: String): Refinanced

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

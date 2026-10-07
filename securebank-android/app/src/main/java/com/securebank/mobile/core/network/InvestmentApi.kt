package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Produto de renda fixa simulado. [termDays] nulo = liquidez diária. */
@Serializable
data class InvestmentProduct(
    val code: String,
    val name: String,
    val kind: String,
    val annualRatePercent: String,
    val termDays: Int? = null,
    val minAmount: String,
)

/** Aplicação. Bruto, rendimento, IR e líquido são "se resgatar agora"; depois do resgate, o que foi pago. */
@Serializable
data class Investment(
    val id: String,
    val accountId: String,
    val productCode: String,
    val productName: String,
    val principal: Money,
    val annualRatePercent: String,
    val termDays: Int? = null,
    val appliedAt: String,
    val maturesAt: String? = null,
    val status: String,
    val daysHeld: Int,
    val gross: Money,
    val yield: Money,
    val tax: Money,
    val taxRatePercent: String,
    val net: Money,
    val canRedeem: Boolean,
    val redeemedAt: String? = null,
) {
    val active: Boolean get() = status == "ACTIVE"
}

@Serializable
data class ApplyInvestmentRequest(val accountId: String, val productCode: String, val amount: String)

interface InvestmentApi {
    @GET("investments/products") suspend fun products(): List<InvestmentProduct>
    @GET("investments") suspend fun list(): List<Investment>
    @GET("investments/{id}") suspend fun get(@Path("id") id: String): Investment

    @POST("investments")
    suspend fun apply(@Body body: ApplyInvestmentRequest, @Header("Idempotency-Key") key: String): Investment

    @POST("investments/{id}/redeem")
    suspend fun redeem(@Path("id") id: String, @Header("Idempotency-Key") key: String): Investment
}

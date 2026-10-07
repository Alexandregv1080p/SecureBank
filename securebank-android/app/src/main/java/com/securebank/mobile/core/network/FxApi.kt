package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/** Cotação simulada: o cliente COMPRA pelo [buyRate] e VENDE pelo [sellRate] (reais por 1 unidade). */
@Serializable
data class FxRate(
    val currency: String,
    val mid: String,
    val buyRate: String,
    val sellRate: String,
    val spreadPercent: String,
    val updatedAt: String,
)

@Serializable
data class FxWallet(val currency: String, val balance: Money)

/** Recibo de uma compra (BUY) ou venda (SELL): [rate] é a cotação aplicada, [brlAmount] o que saiu/entrou em reais. */
@Serializable
data class FxOperation(
    val id: String,
    val accountId: String,
    val side: String,
    val foreignAmount: Money,
    val rate: String,
    val brlAmount: Money,
    val createdAt: String,
) {
    val bought: Boolean get() = side == "BUY"
}

/** [quotedRate] é a cotação que o cliente viu; se mudou no servidor, a operação é recusada. */
@Serializable
data class FxTradeRequest(val accountId: String, val currency: String, val amount: String, val quotedRate: String)

interface FxApi {
    @GET("fx/rates") suspend fun rates(): List<FxRate>
    @GET("fx/wallets") suspend fun wallets(): List<FxWallet>

    @POST("fx/buy")
    suspend fun buy(@Body body: FxTradeRequest, @Header("Idempotency-Key") key: String): FxOperation

    @POST("fx/sell")
    suspend fun sell(@Body body: FxTradeRequest, @Header("Idempotency-Key") key: String): FxOperation

    @GET("fx/operations")
    suspend fun operations(@Query("page") page: Int = 0, @Query("size") size: Int = 10): Page<FxOperation>
}

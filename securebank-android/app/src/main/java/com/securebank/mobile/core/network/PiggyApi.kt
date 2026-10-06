package com.securebank.mobile.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Porquinho: reserva com nome e meta opcional, guardada numa conta. */
@Serializable
data class Piggy(
    val id: String,
    val accountId: String,
    val name: String,
    val balance: Money,
    val goal: Money? = null,
    val progressPercent: Int? = null,
    val goalReached: Boolean = false,
    val status: String = "ACTIVE",
    val createdAt: String,
)

@Serializable
data class CreatePiggyRequest(val accountId: String, val name: String, val goal: String? = null)

/** Campos nulos não são enviados (não mudam); [clearGoal] remove a meta. */
@Serializable
data class UpdatePiggyRequest(val name: String? = null, val goal: String? = null, val clearGoal: Boolean? = null)

interface PiggyApi {
    @GET("piggies") suspend fun list(): List<Piggy>
    @GET("piggies/{id}") suspend fun get(@Path("id") id: String): Piggy
    @POST("piggies") suspend fun create(@Body body: CreatePiggyRequest): Piggy
    @PATCH("piggies/{id}") suspend fun update(@Path("id") id: String, @Body body: UpdatePiggyRequest): Piggy

    @POST("piggies/{id}/deposits")
    suspend fun save(@Path("id") id: String, @Body body: AmountRequest, @Header("Idempotency-Key") key: String): Piggy

    @POST("piggies/{id}/withdrawals")
    suspend fun redeem(@Path("id") id: String, @Body body: AmountRequest, @Header("Idempotency-Key") key: String): Piggy

    @DELETE("piggies/{id}") suspend fun close(@Path("id") id: String)
}

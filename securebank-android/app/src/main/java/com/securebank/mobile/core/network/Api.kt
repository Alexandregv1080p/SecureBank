package com.securebank.mobile.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query

/** Rotas de autenticação não levam o access token e nunca disparam renovação (cabeçalho No-Auth, removido pelo interceptor). */
interface AuthApi {
    @Headers("No-Auth: true") @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @Headers("No-Auth: true") @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @Headers("No-Auth: true") @POST("auth/mfa/verify")
    suspend fun verifyMfa(@Body body: MfaVerifyRequest): TokenResponse

    @Headers("No-Auth: true") @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout()
}

interface BankingApi {
    @GET("customers/me") suspend fun me(): Customer

    @GET("accounts") suspend fun accounts(): List<Account>
    @GET("accounts/{id}") suspend fun account(@Path("id") id: String): Account
    @POST("accounts") suspend fun openAccount(@Body body: OpenAccountRequest): Account
    @GET("accounts/{id}/limits") suspend fun limits(@Path("id") id: String): List<LimitUsage>

    @GET("accounts/{id}/statement")
    suspend fun statement(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("size") size: Int = 10,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): Page<Transaction>

    @POST("accounts/{id}/deposits")
    suspend fun deposit(@Path("id") id: String, @Body body: AmountRequest, @Header("Idempotency-Key") key: String): Transaction

    @POST("accounts/{id}/withdrawals")
    suspend fun withdraw(@Path("id") id: String, @Body body: AmountRequest, @Header("Idempotency-Key") key: String): Transaction

    @POST("transfers")
    suspend fun transfer(@Body body: TransferRequest, @Header("Idempotency-Key") key: String): Transfer
    @GET("transfers") suspend fun transfers(@Query("page") page: Int = 0, @Query("size") size: Int = 10): Page<Transfer>

    @POST("payments")
    suspend fun pay(@Body body: PaymentRequest, @Header("Idempotency-Key") key: String): Payment
    @GET("payments") suspend fun payments(@Query("page") page: Int = 0, @Query("size") size: Int = 10): Page<Payment>

    @GET("notifications") suspend fun notifications(@Query("page") page: Int = 0, @Query("size") size: Int = 20): Page<AppNotification>
    @POST("notifications/{id}/read") suspend fun markRead(@Path("id") id: String)
}

interface SecurityApi {
    @GET("security/mfa") suspend fun mfaStatus(): MfaStatus
    @GET("security/sessions") suspend fun sessions(): List<SessionInfo>
    @DELETE("security/sessions/{id}") suspend fun revokeSession(@Path("id") id: String)
    @POST("security/password") suspend fun changePassword(@Body body: ChangePasswordRequest)
    @POST("security/mfa") suspend fun setupMfa(): MfaSetup
    @POST("security/mfa/confirm") suspend fun confirmMfa(@Body body: CodeRequest)

    // DELETE com corpo (o código TOTP): o Retrofit só permite via @HTTP
    @HTTP(method = "DELETE", path = "security/mfa", hasBody = true)
    suspend fun disableMfa(@Body body: CodeRequest)
}

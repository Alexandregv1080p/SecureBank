package com.securebank.mobile.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** Corpo de erro padrão da API (com traceId, o mesmo do header X-Trace-Id). */
@Serializable
data class ErrorBody(val code: String? = null, val message: String? = null, val traceId: String? = null)

class ApiError(
    val status: Int,
    val code: String,
    message: String,
    val traceId: String? = null,
    val network: Boolean = false,
    val retryAfterSeconds: Int? = null,
) : Exception(message) {

    /** Resultado incerto: a operação pode ou não ter sido executada (vale reutilizar a Idempotency-Key). */
    val uncertain: Boolean get() = network || status >= 500 || status == 429 || status == 409

    /** Falha de dependência do servidor (503 + Retry-After): pedir para tentar de novo, nunca "sessão inválida". */
    val unavailable: Boolean get() = status == 503
}

/** Executa uma chamada Retrofit e traduz qualquer falha em [ApiError] (a UI só conhece este tipo). */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw e.toApiError(json)
} catch (e: IOException) {
    throw ApiError(0, "NETWORK", "Sem conexão", network = true)
} catch (e: SerializationException) {
    throw ApiError(0, "BAD_RESPONSE", "Resposta inesperada do servidor")
}

private fun HttpException.toApiError(json: Json): ApiError {
    val response = response()
    val body = runCatching {
        response?.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<ErrorBody>(it) }
    }.getOrNull()
    return ApiError(
        status = code(),
        code = body?.code ?: "ERROR",
        message = body?.message ?: "Erro",
        traceId = body?.traceId,
        retryAfterSeconds = response?.headers()?.get("Retry-After")?.toIntOrNull(),
    )
}

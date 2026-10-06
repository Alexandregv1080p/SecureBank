package com.securebank.mobile.ui

import com.securebank.mobile.core.network.messageFor
import kotlin.coroutines.cancellation.CancellationException

/** Estado de um carregamento: toda tela mostra os três (esqueleto, erro com "tentar de novo", dados). */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** Como runCatching, mas sem engolir o cancelamento da corrotina (senão a tela "falha" ao sair dela). */
suspend inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun <T> Result<T>.toLoad(): Load<T> = fold({ Load.Ready(it) }, { Load.Failed(messageFor(it)) })

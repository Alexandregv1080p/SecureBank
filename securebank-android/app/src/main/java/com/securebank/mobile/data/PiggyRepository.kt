package com.securebank.mobile.data

import com.securebank.mobile.core.network.AmountRequest
import com.securebank.mobile.core.network.CreatePiggyRequest
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.core.network.PiggyApi
import com.securebank.mobile.core.network.UpdatePiggyRequest
import com.securebank.mobile.core.network.apiCall
import kotlinx.serialization.json.Json

/**
 * Porquinhos. Qualquer mudança avisa as telas pelo [BankingRepository.changes] (o saldo da conta e a lista de porquinhos
 * se mexem juntos), então Início, Contas e porquinhos recarregam sozinhos.
 */
class PiggyRepository(private val api: PiggyApi, private val json: Json, private val banking: BankingRepository) {

    suspend fun list(): List<Piggy> = apiCall(json) { api.list() }

    suspend fun get(id: String): Piggy = apiCall(json) { api.get(id) }

    suspend fun create(accountId: String, name: String, goal: String?): Piggy =
        apiCall(json) { api.create(CreatePiggyRequest(accountId, name.trim(), goal)) }.also { banking.notifyChanged() }

    suspend fun rename(id: String, name: String): Piggy =
        apiCall(json) { api.update(id, UpdatePiggyRequest(name = name.trim())) }.also { banking.notifyChanged() }

    suspend fun setGoal(id: String, goal: String): Piggy =
        apiCall(json) { api.update(id, UpdatePiggyRequest(goal = goal)) }.also { banking.notifyChanged() }

    suspend fun clearGoal(id: String): Piggy =
        apiCall(json) { api.update(id, UpdatePiggyRequest(clearGoal = true)) }.also { banking.notifyChanged() }

    /** Guarda dinheiro: sai da conta e entra no porquinho. [key]: Idempotency-Key da intenção. */
    suspend fun save(id: String, amount: String, key: String): Piggy =
        apiCall(json) { api.save(id, AmountRequest(amount), key) }.also { banking.notifyChanged() }

    /** Resgata: volta para a conta. */
    suspend fun redeem(id: String, amount: String, key: String): Piggy =
        apiCall(json) { api.redeem(id, AmountRequest(amount), key) }.also { banking.notifyChanged() }

    /** Fecha o porquinho; o que houver dentro volta para a conta. */
    suspend fun close(id: String) {
        apiCall(json) { api.close(id) }
        banking.notifyChanged()
    }
}

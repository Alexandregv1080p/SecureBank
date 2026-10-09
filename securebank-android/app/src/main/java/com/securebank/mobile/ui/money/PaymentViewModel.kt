package com.securebank.mobile.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.Payment
import com.securebank.mobile.core.network.PaymentRequest
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.OperationValidation
import com.securebank.mobile.core.util.PaymentField
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentState(
    val accounts: Load<List<Account>> = Load.Loading,
    val recent: Load<List<Payment>> = Load.Loading,
    val accountId: String? = null,
    val barcode: String = "",
    val amount: String = "",
    val description: String = "",
    val errors: Map<PaymentField, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val ok: String? = null,
    /** Limite de pagamento da conta escolhida (mostrado e conferido antes de pagar). */
    val limit: com.securebank.mobile.core.network.LimitUsage? = null,
)

class PaymentViewModel(private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(PaymentState())
    val state: StateFlow<PaymentState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            val accounts = attempt { banking.accounts() }
            _state.update { s ->
                val load = accounts.toLoad()
                val only = (load as? Load.Ready)?.value?.singleOrNull()?.id
                s.copy(accounts = load, accountId = s.accountId ?: only)
            }
            _state.value.accountId?.let { if (_state.value.limit == null) loadLimit(it) }
        }
        viewModelScope.launch {
            val recent = attempt { banking.payments(0).items }
            _state.update { it.copy(recent = recent.toLoad()) }
        }
    }

    private fun edit(block: (PaymentState) -> PaymentState) = _state.update { block(it).copy(error = null, ok = null) }

    fun onAccount(id: String) {
        edit { it.copy(accountId = id, limit = null, errors = it.errors - PaymentField.Account - PaymentField.Amount) }
        loadLimit(id)
    }

    private fun loadLimit(accountId: String) {
        viewModelScope.launch {
            val limit = attempt { banking.limits(accountId) }.getOrNull()?.firstOrNull { it.type == "PAYMENT" }
            _state.update { if (it.accountId == accountId) it.copy(limit = limit) else it }
        }
    }
    fun onBarcode(v: String) = edit { it.copy(barcode = v.filter(Char::isDigit).take(48), errors = it.errors - PaymentField.Barcode) }
    fun onAmount(v: String) = edit { it.copy(amount = com.securebank.mobile.core.util.Validation.sanitizeAmount(v), errors = it.errors - PaymentField.Amount) }
    fun onDescription(v: String) = edit { it.copy(description = v.take(140), errors = it.errors - PaymentField.Description) }

    /** Valida; devolve true se pode seguir para a confirmação de identidade. */
    fun validate(): Boolean {
        val s = _state.value
        val source = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == s.accountId }
        val errors = OperationValidation.payment(s.accountId, s.barcode, s.amount, s.description, source, s.limit)
        _state.update { it.copy(errors = errors) }
        return errors.isEmpty()
    }

    /** Só chamar DEPOIS de [validate] e da confirmação de identidade. */
    fun pay() {
        val s = _state.value
        if (s.loading) return
        val request = PaymentRequest(
            accountId = s.accountId ?: return,
            amount = Money.parse(s.amount) ?: return,
            barcode = OperationValidation.digits(s.barcode),
            description = s.description.trim().ifEmpty { null },
        )
        _state.update { it.copy(loading = true, error = null, ok = null) }
        viewModelScope.launch {
            try {
                intent.run(request) { key -> banking.pay(request, key) }
                _state.update { it.copy(barcode = "", amount = "", description = "", ok = "Pagamento de ${Money.format(request.amount)} realizado.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}

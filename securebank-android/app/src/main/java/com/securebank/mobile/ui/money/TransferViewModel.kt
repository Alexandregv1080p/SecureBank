package com.securebank.mobile.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.util.Validation
import com.securebank.mobile.core.network.TransferRequest
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.OperationValidation
import com.securebank.mobile.core.util.TransferField
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

enum class TransferStep { Form, Review, Sent }

data class TransferState(
    val accounts: Load<List<Account>> = Load.Loading,
    val step: TransferStep = TransferStep.Form,
    val sourceId: String? = null,
    val branch: String = "0001",
    val number: String = "",
    val amount: String = "",
    val description: String = "",
    /** Limite de transferência da conta de origem (mostrado e conferido antes de enviar). */
    val limit: LimitUsage? = null,
    val errors: Map<TransferField, String> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Valor e destino da última transferência enviada (para a tela de sucesso). */
    val sent: String? = null,
)

/** Formulário → revisão → confirmação (com biometria na tela) → comprovante. */
class TransferViewModel(private val banking: BankingRepository) : ViewModel() {
    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    init {
        loadAccounts()
        viewModelScope.launch { banking.changes.collect { loadAccounts() } } // saldos no seletor sempre atuais
    }

    fun loadAccounts() {
        viewModelScope.launch {
            val result = attempt { banking.accounts() }
            _state.update { s ->
                val load = result.toLoad()
                // com uma conta só, já vem escolhida
                val only = (load as? Load.Ready)?.value?.singleOrNull()?.id
                s.copy(accounts = load, sourceId = s.sourceId ?: only)
            }
            _state.value.sourceId?.let { if (_state.value.limit == null) loadLimit(it) }
        }
    }

    private fun loadLimit(accountId: String) {
        viewModelScope.launch {
            val limit = attempt { banking.limits(accountId) }.getOrNull()?.firstOrNull { it.type == "TRANSFER" }
            _state.update { if (it.sourceId == accountId) it.copy(limit = limit) else it }
        }
    }

    private fun edit(block: (TransferState) -> TransferState) = _state.update { block(it).copy(error = null) }

    fun onSource(id: String) {
        edit { it.copy(sourceId = id, limit = null, errors = it.errors - TransferField.Source - TransferField.Amount - TransferField.Number) }
        loadLimit(id)
    }
    fun onBranch(v: String) = edit { it.copy(branch = v.filter(Char::isDigit).take(4), errors = it.errors - TransferField.Branch) }
    fun onNumber(v: String) = edit { it.copy(number = Validation.maskAccountNumber(v), errors = it.errors - TransferField.Number) }
    fun onAmount(v: String) = edit { it.copy(amount = Validation.sanitizeAmount(v), errors = it.errors - TransferField.Amount) }
    fun onDescription(v: String) = edit { it.copy(description = v.take(140), errors = it.errors - TransferField.Description) }

    fun review() {
        val s = _state.value
        val source = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == s.sourceId }
        val errors = OperationValidation.transfer(s.sourceId, s.branch, s.number, s.amount, s.description, source, s.limit)
        _state.update { if (errors.isEmpty()) it.copy(step = TransferStep.Review, errors = emptyMap()) else it.copy(errors = errors) }
    }

    fun backToForm() = _state.update { it.copy(step = TransferStep.Form, error = null) }

    /** Só chamar DEPOIS da confirmação de identidade. */
    fun confirm() {
        val s = _state.value
        if (s.loading || s.step != TransferStep.Review) return
        val request = TransferRequest(
            sourceAccountId = s.sourceId!!,
            destinationBranch = s.branch,
            destinationAccountNumber = s.number,
            amount = Money.parse(s.amount)!!,
            description = s.description.trim().ifEmpty { null },
        )
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                intent.run(request) { key -> banking.transfer(request, key) }
                _state.update {
                    it.copy(
                        step = TransferStep.Sent,
                        sent = "${Money.format(request.amount)} para Ag. ${request.destinationBranch}, conta ${request.destinationAccountNumber}",
                        amount = "", number = "", description = "",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun newTransfer() = _state.update { it.copy(step = TransferStep.Form, sent = null, error = null) }
}

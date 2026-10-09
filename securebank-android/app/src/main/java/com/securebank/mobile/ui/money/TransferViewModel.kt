package com.securebank.mobile.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.ApiError
import com.securebank.mobile.core.network.Destination
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.Transfer
import com.securebank.mobile.core.util.Format
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

/** O que o comprovante mostra: a transferência que o servidor devolveu, quem recebeu e de qual conta saiu. */
data class TransferReceipt(val transfer: Transfer, val destination: Destination, val from: String)

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
    /** Quem recebe, já confirmado pelo servidor (nome e CPF mascarados): aparece na revisão. */
    val destination: Destination? = null,
    /** Comprovante da última transferência enviada (para a tela de sucesso). */
    val receipt: TransferReceipt? = null,
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

    /** Valida o formulário e pergunta ao servidor quem é o destinatário; só então abre a revisão. */
    fun review() {
        val s = _state.value
        if (s.loading) return
        val source = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == s.sourceId }
        val errors = OperationValidation.transfer(s.sourceId, s.branch, s.number, s.amount, s.description, source, s.limit)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(loading = true, errors = emptyMap(), error = null) }
        viewModelScope.launch {
            try {
                val found = banking.destination(s.branch, s.number)
                val destination = found.copy(name = com.securebank.mobile.core.util.Dashboard.titleCase(found.name)) // o cadastro guarda como foi digitado
                if (destination.status != "ACTIVE") {
                    _state.update { it.copy(errors = mapOf(TransferField.Number to "Esta conta não está disponível para receber.")) }
                } else {
                    _state.update { it.copy(step = TransferStep.Review, destination = destination) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                if (e.status == 404) _state.update { it.copy(errors = mapOf(TransferField.Number to "Conta não encontrada. Confira a agência e o número.")) }
                else _state.update { it.copy(error = messageFor(e)) }
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun backToForm() = _state.update { it.copy(step = TransferStep.Form, destination = null, error = null) }

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
                val transfer = intent.run(request) { key -> banking.transfer(request, key) }
                val from = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == s.sourceId }
                    ?.let { "${Format.accountTypeLabel(it.type)}, ${Format.accountLabel(it.branch, it.accountNumber)}" }.orEmpty()
                _state.update {
                    it.copy(
                        step = TransferStep.Sent,
                        receipt = it.destination?.let { d -> TransferReceipt(transfer, d, from) },
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

    fun newTransfer() = _state.update { it.copy(step = TransferStep.Form, receipt = null, destination = null, error = null) }
}

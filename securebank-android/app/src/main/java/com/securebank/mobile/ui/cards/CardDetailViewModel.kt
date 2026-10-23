package com.securebank.mobile.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.network.CardDetails
import com.securebank.mobile.core.network.CardPurchase
import com.securebank.mobile.core.network.CreditPurchase
import com.securebank.mobile.core.network.CreditSummary
import com.securebank.mobile.core.network.Invoice
import com.securebank.mobile.core.network.InvoiceDetail
import com.securebank.mobile.core.network.RefinanceOffer
import com.securebank.mobile.core.util.CreditValidation
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.CardValidation
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.Validation
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.CardRepository
import com.securebank.mobile.data.CreditRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Quanto tempo o número e o CVV ficam na tela antes de se esconderem sozinhos. */
const val REVEAL_SECONDS = 30

enum class CardAction { BLOCK, UNBLOCK, CANCEL }

data class CardDetailState(
    val card: Load<Card> = Load.Loading,
    val account: Account? = null,
    val limit: LimitUsage? = null,
    val purchases: List<CardPurchase> = emptyList(),
    // crédito
    val summary: CreditSummary? = null,
    val invoices: List<Invoice> = emptyList(),
    val creditPurchases: List<CreditPurchase> = emptyList(),
    val installments: Int = 1,
    /** Fatura aberta na lista (mostra os itens) e os itens já carregados. */
    val openInvoice: String? = null,
    val invoiceDetails: Map<String, InvoiceDetail> = emptyMap(),
    /** Fatura que o cliente está pagando (a tela mostra a janela de pagamento). */
    val payTarget: Invoice? = null,
    val payAmount: String = "",
    /** ALL, MIN ou OTHER. */
    val payMode: String = "ALL",
    val payError: String? = null,
    /** Fatura que o cliente quer parcelar, as opções do servidor e a escolhida. */
    val splitTarget: Invoice? = null,
    val splitOffer: RefinanceOffer? = null,
    val splitChoice: Int? = null,
    val limitInput: String = "",
    val limitError: String? = null,
    val additionalName: String = "",
    val additionalError: String? = null,
    val details: CardDetails? = null,
    val secondsLeft: Int = 0,
    val merchant: String = "",
    val amount: String = "",
    val merchantError: String? = null,
    val amountError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: String? = null,
)

class CardDetailViewModel(
    private val repository: CardRepository,
    private val credit: CreditRepository,
    private val banking: BankingRepository,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow(CardDetailState())
    val state: StateFlow<CardDetailState> = _state.asStateFlow()
    private val intent = IdempotentIntent()
    private var countdown: Job? = null

    private data class Payload(val id: String, val amount: String, val merchant: String)

    init {
        load()
        viewModelScope.launch { banking.changes.collect { reload() } }
    }

    fun load() {
        _state.update { it.copy(card = Load.Loading) }
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val card = attempt { repository.get(id) }
            val c = card.getOrNull()
            val account = c?.let { x -> attempt { banking.accounts() }.getOrNull()?.firstOrNull { it.id == x.accountId } }
            if (c?.kind == "CREDIT") {
                val summary = attempt { credit.summary(id) }.getOrNull()
                val invoices = attempt { credit.invoices(id) }.getOrNull().orEmpty()
                val purchases = attempt { credit.purchases(id) }.getOrNull().orEmpty()
                _state.update { it.copy(card = card.toLoad(), account = account, summary = summary, invoices = invoices, creditPurchases = purchases, invoiceDetails = emptyMap()) }
                return@launch
            }
            val limit = c?.let { attempt { banking.limits(it.accountId) }.getOrNull()?.firstOrNull { l -> l.type == "CARD" } }
            val purchases = attempt { repository.purchases(id) }.getOrNull().orEmpty()
            _state.update { it.copy(card = card.toLoad(), account = account, limit = limit, purchases = purchases) }
        }
    }

    fun onMerchant(v: String) = _state.update { it.copy(merchant = v.take(CardValidation.MERCHANT_MAX + 5), merchantError = null, error = null, done = null) }

    fun onInstallments(n: Int) = _state.update { it.copy(installments = n) }

    /** Abre ou fecha os itens de uma fatura (carrega do servidor na primeira vez). */
    fun toggleInvoice(month: String) {
        if (_state.value.openInvoice == month) {
            _state.update { it.copy(openInvoice = null) }
            return
        }
        _state.update { it.copy(openInvoice = month) }
        if (month in _state.value.invoiceDetails) return
        viewModelScope.launch {
            attempt { credit.invoice(id, month) }.getOrNull()?.let { d -> _state.update { it.copy(invoiceDetails = it.invoiceDetails + (month to d)) } }
        }
    }

    fun openPay(invoice: Invoice) = _state.update { it.copy(payTarget = invoice, payAmount = "", payMode = "ALL", payError = null) }
    fun closePay() = _state.update { it.copy(payTarget = null, payError = null) }
    fun onPayMode(mode: String) = _state.update { it.copy(payMode = mode, payError = null) }
    fun onPayAmount(raw: String) = _state.update { it.copy(payAmount = Validation.sanitizeAmount(raw), payError = null) }

    /** Paga a fatura aberta na janela: inteira ou o valor digitado; a tela já pediu a biometria. */
    fun payInvoice() {
        val s = _state.value
        val invoice = s.payTarget ?: return
        if (s.loading) return
        val remaining = invoice.remaining.amount
        val value = when (s.payMode) {
            "ALL" -> remaining
            "MIN" -> invoice.minimumDue.amount
            else -> Money.parse(s.payAmount)
        }
        val issue = if (s.payMode == "OTHER") CreditValidation.payIssue(s.payAmount, remaining) else null
        if (value == null || issue != null) {
            _state.update { it.copy(payError = issue ?: Validation.amountIssue(s.payAmount)) }
            return
        }
        runCatchingUi {
            intent.run(Payload(id, value, invoice.month)) { key -> credit.payInvoice(id, invoice.month, value, key) }
            _state.update { it.copy(payTarget = null, done = "Pagamento de ${Money.format(value)} da fatura de ${CreditValidation.monthLabel(invoice.month)} feito.") }
        }
    }

    /** Abre a janela de parcelamento e busca as opções (com juros) no servidor. */
    fun openSplit(invoice: Invoice) {
        _state.update { it.copy(splitTarget = invoice, splitOffer = null, splitChoice = null, error = null) }
        viewModelScope.launch {
            try {
                val offer = credit.refinanceOptions(id, invoice.month)
                _state.update { it.copy(splitOffer = offer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(splitTarget = null, error = messageFor(e)) }
            }
        }
    }

    fun closeSplit() = _state.update { it.copy(splitTarget = null, splitOffer = null, splitChoice = null) }
    fun onSplitChoice(n: Int) = _state.update { it.copy(splitChoice = n) }

    /** Parcela a fatura na opção escolhida; a tela já pediu a biometria. */
    fun refinance() {
        val s = _state.value
        val invoice = s.splitTarget ?: return
        val n = s.splitChoice ?: return
        if (s.loading) return
        runCatchingUi {
            intent.run(Payload(id, n.toString(), invoice.month)) { key -> credit.refinance(id, invoice.month, n, key) }
            _state.update { it.copy(splitTarget = null, splitOffer = null, splitChoice = null, done = "Fatura de ${CreditValidation.monthLabel(invoice.month)} parcelada em ${n}x.") }
        }
    }

    fun onLimit(raw: String) = _state.update { it.copy(limitInput = Validation.sanitizeAmount(raw), limitError = null, error = null, done = null) }

    fun changeLimit() {
        val s = _state.value
        if (s.loading) return
        val issue = CreditValidation.limitIssue(s.limitInput, s.summary?.used?.amount ?: "0")
        if (issue != null) {
            _state.update { it.copy(limitError = issue) }
            return
        }
        val value = Money.parse(s.limitInput)!!
        runCatchingUi {
            credit.changeLimit(id, value)
            _state.update { it.copy(limitInput = "", done = "Novo limite: ${Money.format(value)}.") }
        }
    }

    fun onAdditionalName(v: String) = _state.update { it.copy(additionalName = v.take(40), additionalError = null, error = null, done = null) }

    fun addAdditional() {
        val s = _state.value
        if (s.loading) return
        val issue = CreditValidation.holderIssue(s.additionalName)
        if (issue != null) {
            _state.update { it.copy(additionalError = issue) }
            return
        }
        runCatchingUi {
            val card = credit.addAdditional(id, s.additionalName)
            _state.update { it.copy(additionalName = "", done = "Cartão adicional final ${card.last4} criado. Ele está na lista de cartões.") }
        }
    }

    fun onAmount(raw: String) = _state.update { it.copy(amount = Validation.sanitizeAmount(raw), amountError = null, error = null, done = null) }

    /** Mostra número e CVV por [REVEAL_SECONDS] segundos; a tela pede a biometria antes de chamar. */
    fun reveal() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val details = repository.reveal(id)
                _state.update { it.copy(details = details, secondsLeft = REVEAL_SECONDS) }
                startCountdown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = messageFor(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun hide() {
        countdown?.cancel()
        _state.update { it.copy(details = null, secondsLeft = 0) }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (_state.value.secondsLeft > 0) {
                delay(1000)
                _state.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            _state.update { it.copy(details = null) }
        }
    }

    /** [reason] é obrigatório para bloquear e cancelar (a tela já o pediu); desbloquear não precisa. */
    fun change(action: CardAction, reason: String? = null) {
        if (_state.value.loading) return
        hide()
        runCatchingUi {
            when (action) {
                CardAction.BLOCK -> repository.block(id, reason.orEmpty())
                CardAction.UNBLOCK -> repository.unblock(id)
                CardAction.CANCEL -> repository.cancel(id, reason.orEmpty())
            }
            _state.update { it.copy(done = when (action) { CardAction.BLOCK -> "Cartão bloqueado."; CardAction.UNBLOCK -> "Cartão desbloqueado."; CardAction.CANCEL -> "Cartão cancelado." }) }
        }
    }

    /** Compra simulada no débito; a tela pede a biometria antes de chamar. */
    fun purchase() {
        val s = _state.value
        if (s.loading) return
        val isCredit = (s.card as? Load.Ready)?.value?.kind == "CREDIT"
        val merchantError = CardValidation.merchantIssue(s.merchant)
        val amountError = if (isCredit) CreditValidation.purchaseAmountIssue(s.amount, s.installments, s.summary?.available?.amount)
        else CardValidation.amountIssue(s.amount, s.account?.balance?.amount, s.limit)
        if (merchantError != null || amountError != null) {
            _state.update { it.copy(merchantError = merchantError, amountError = amountError) }
            return
        }
        val value = Money.parse(s.amount)!!
        val name = s.merchant.trim()
        runCatchingUi {
            if (isCredit) {
                val n = s.installments
                intent.run(Payload(id, value, name + "|" + n)) { key -> credit.purchase(id, value, name, n, key) }
                _state.update { it.copy(amount = "", merchant = "", installments = 1, done = "Compra de ${Money.format(value)} em $name${if (n > 1) ", em ${n}x," else ""} aprovada.") }
            } else {
                intent.run(Payload(id, value, name)) { key -> repository.purchase(id, value, name, key) }
                _state.update { it.copy(amount = "", merchant = "", done = "Compra de ${Money.format(value)} em $name aprovada.") }
            }
        }
    }

    private fun runCatchingUi(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null, done = null) }
        viewModelScope.launch {
            try {
                block()
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

package com.securebank.mobile.ui.invest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.Investment
import com.securebank.mobile.core.network.InvestmentProduct
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.core.util.IdempotentIntent
import com.securebank.mobile.core.util.InvestmentValidation
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.InvestmentRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApplyInvestmentState(
    val product: Load<InvestmentProduct> = Load.Loading,
    val accounts: Load<List<Account>> = Load.Loading,
    val accountId: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val done: Investment? = null,
)

class ApplyInvestmentViewModel(
    private val repository: InvestmentRepository,
    private val banking: BankingRepository,
    private val productCode: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ApplyInvestmentState())
    val state: StateFlow<ApplyInvestmentState> = _state.asStateFlow()
    private val intent = IdempotentIntent()

    private data class Payload(val account: String, val product: String, val amount: String)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val product = attempt { repository.products().first { it.code == productCode } }
            val accounts = attempt { banking.accounts() }
            _state.update { s ->
                val list = accounts.getOrNull()
                s.copy(
                    product = product.fold({ Load.Ready(it) }, { Load.Failed(messageFor(it)) }),
                    accounts = accounts.fold({ Load.Ready(it) }, { Load.Failed(messageFor(it)) }),
                    accountId = s.accountId ?: list?.singleOrNull()?.id,
                )
            }
        }
    }

    fun onAccount(id: String) = _state.update { it.copy(accountId = id, error = null) }
    fun onAmount(v: String) = _state.update { it.copy(amount = com.securebank.mobile.core.util.Validation.sanitizeAmount(v), amountError = null, error = null) }

    /** Valida; a tela pede a biometria e só então chama [confirm]. Devolve true se está tudo certo para confirmar. */
    fun validate(): Boolean {
        val s = _state.value
        val product = (s.product as? Load.Ready)?.value ?: return false
        val available = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == s.accountId }?.balance?.amount
        val error = InvestmentValidation.amountError(s.amount, product, available)
        _state.update { it.copy(amountError = error, error = if (s.accountId == null) "Escolha a conta de onde sai o dinheiro." else null) }
        return error == null && s.accountId != null
    }

    /** Só chamar DEPOIS da confirmação de identidade. */
    fun confirm() {
        val s = _state.value
        if (s.loading) return
        val amount = Money.parse(s.amount) ?: return
        val account = s.accountId ?: return
        val payload = Payload(account, productCode, amount)
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val investment = intent.run(payload) { key -> repository.apply(payload.account, payload.product, payload.amount, key) }
                _state.update { it.copy(done = investment) }
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

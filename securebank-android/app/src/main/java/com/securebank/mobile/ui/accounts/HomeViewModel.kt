package com.securebank.mobile.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.FxWallet
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.core.network.StatementSummary
import com.securebank.mobile.core.network.Transaction
import com.securebank.mobile.core.util.Dashboard
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.data.FxRepository
import com.securebank.mobile.data.InvestmentRepository
import com.securebank.mobile.data.PiggyRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Um lançamento recente, com o nome da conta onde aconteceu. */
data class RecentTransaction(val transaction: Transaction, val accountLabel: String)

/** Tudo o que o dashboard mostra além das contas; se algo falhar, só aquele bloco fica de fora (nada é crítico). */
data class Overview(
    val months: List<Dashboard.MonthTotals> = emptyList(),
    val shares: List<Dashboard.Share> = emptyList(),
    val recent: List<RecentTransaction> = emptyList(),
    val invested: String = "0.00",
    val wallets: List<FxWallet> = emptyList(),
) {
    val current: Dashboard.MonthTotals? get() = months.lastOrNull()
    val previous: Dashboard.MonthTotals? get() = months.getOrNull(months.size - 2)
    val hasMovement: Boolean get() = months.any { it.income > 0 || it.expenses > 0 }
}

data class HomeState(
    val firstName: String? = null,
    val name: String? = null,
    val accounts: Load<List<Account>> = Load.Loading,
    /** Resumo na tela inicial: se falhar, a seção só não aparece (não é crítica). */
    val piggies: List<Piggy> = emptyList(),
    val overview: Overview = Overview(),
    val overviewLoading: Boolean = true,
    val refreshing: Boolean = false,
)

class HomeViewModel(
    private val banking: BankingRepository,
    private val piggies: PiggyRepository,
    private val investments: InvestmentRepository,
    private val fx: FxRepository,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("America/Sao_Paulo")) },
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { banking.changes.collect { load() } } // depois de operar, o saldo já aparece novo
    }

    fun refresh() = load()

    private fun load() {
        _state.update { if (it.accounts is Load.Ready) it.copy(refreshing = true) else it.copy(accounts = Load.Loading) }
        viewModelScope.launch {
            val (accounts, me, savings) = coroutineScope {
                val a = async { attempt { banking.accounts() } }
                val m = async { attempt { banking.me() } }
                val p = async { attempt { piggies.list() } }
                Triple(a.await(), m.await(), p.await())
            }
            _state.update {
                it.copy(
                    accounts = accounts.toLoad(),
                    firstName = me.getOrNull()?.let { c -> Format.firstName(c.name) } ?: it.firstName,
                    name = me.getOrNull()?.name ?: it.name,
                    piggies = savings.getOrNull() ?: it.piggies,
                    refreshing = false,
                )
            }
            accounts.getOrNull()?.let { loadOverview(it) }
        }
    }

    /** Resumos dos últimos 6 meses (um por conta e por mês), lançamentos recentes, investido e carteiras em moeda. */
    private suspend fun loadOverview(accounts: List<Account>) {
        _state.update { it.copy(overviewLoading = it.overview.months.isEmpty()) }
        val months = Dashboard.lastMonths(6, today())
        val (summaries, recent, invested, wallets) = coroutineScope {
            val s = accounts.map { a -> months.map { m -> async { attempt { banking.statementSummary(a.id, m) }.getOrNull() } } }
            val r = accounts.map { a -> async { attempt { banking.statement(a.id, 0, size = 8) }.getOrNull()?.items.orEmpty().map { t -> RecentTransaction(t, Format.accountTypeLabel(a.type)) } } }
            val i = async { attempt { investments.list() }.getOrNull() }
            val w = async { attempt { fx.wallets() }.getOrNull() }
            Quad(s.map { row -> row.awaitAll() }, r.awaitAll().flatten(), i.await(), w.await())
        }
        // perMonth[mês][conta]
        val perMonth: List<List<StatementSummary?>> = months.indices.map { mi -> summaries.map { it[mi] } }
        val overview = Overview(
            months = Dashboard.combineMonths(months, perMonth),
            shares = Dashboard.expenseShares(perMonth.last()),
            recent = recent.sortedByDescending { it.transaction.createdAt }.take(7),
            invested = Dashboard.total(*(invested.orEmpty().filter { it.active }.map { it.net.amount }.toTypedArray())).toPlainString(),
            wallets = wallets.orEmpty(),
        )
        _state.update { it.copy(overview = overview, overviewLoading = false) }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

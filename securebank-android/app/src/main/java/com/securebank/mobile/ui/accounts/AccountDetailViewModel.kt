package com.securebank.mobile.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.StatementSummary
import com.securebank.mobile.core.network.Transaction
import com.securebank.mobile.core.network.messageFor
import com.securebank.mobile.data.BankingRepository
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.attempt
import com.securebank.mobile.ui.toLoad
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Extrato acumulado: "Ver mais" acrescenta a próxima página; mudar o período recomeça da primeira. */
data class StatementState(
    val items: List<Transaction> = emptyList(),
    val total: Long = 0,
    val nextPage: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val rangeError: String? = null,
    /** Filtros do servidor: categoria (CASH, TRANSFERS, PAYMENTS, PIX, SAVINGS) e sentido (CREDIT, DEBIT); nulo = todos. */
    val category: String? = null,
    val direction: String? = null,
) {
    val hasMore: Boolean get() = items.size < total
}

data class AccountDetailState(
    val account: Load<Account> = Load.Loading,
    val limits: Load<List<LimitUsage>> = Load.Loading,
    val statement: StatementState = StatementState(),
    val refreshing: Boolean = false,
    val month: YearMonth = YearMonth.now(ZoneId.of("America/Sao_Paulo")),
    val summary: Load<StatementSummary> = Load.Loading,
    val exporting: Boolean = false,
    val exportMessage: String? = null,
) {
    /** Não dá para ver o futuro: o resumo para no mês atual. */
    val canGoNext: Boolean get() = month.isBefore(YearMonth.now(ZoneId.of("America/Sao_Paulo")))
}

class AccountDetailViewModel(private val banking: BankingRepository, private val accountId: String) : ViewModel() {
    private val _state = MutableStateFlow(AccountDetailState())
    val state: StateFlow<AccountDetailState> = _state.asStateFlow()

    private var statementJob: Job? = null

    init {
        loadHeader()
        loadStatement(reset = true)
        viewModelScope.launch {
            banking.changes.collect {
                loadHeader()
                loadStatement(reset = true)
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        loadHeader()
        loadStatement(reset = true)
    }

    fun setRange(from: LocalDate?, to: LocalDate?) {
        if (from != null && to != null && from.isAfter(to)) {
            _state.update { it.copy(statement = it.statement.copy(rangeError = "A data inicial é depois da final.")) }
            return
        }
        _state.update { it.copy(statement = it.statement.copy(from = from, to = to, rangeError = null)) }
        loadStatement(reset = true)
    }

    fun setCategory(category: String?) {
        _state.update { it.copy(statement = it.statement.copy(category = category)) }
        loadStatement(reset = true)
    }

    fun setDirection(direction: String?) {
        _state.update { it.copy(statement = it.statement.copy(direction = direction)) }
        loadStatement(reset = true)
    }

    fun changeMonth(delta: Long) {
        val next = _state.value.month.plusMonths(delta)
        if (delta > 0 && !_state.value.canGoNext) return
        _state.update { it.copy(month = next) }
        loadSummary()
    }

    fun clearExportMessage() = _state.update { it.copy(exportMessage = null) }

    /** Baixa o CSV com os filtros atuais e entrega a [write] (que grava no arquivo escolhido pelo usuário). */
    fun export(write: suspend (String) -> Unit) {
        val s = _state.value
        if (s.exporting) return
        _state.update { it.copy(exporting = true, exportMessage = null) }
        viewModelScope.launch {
            try {
                val st = s.statement
                write(banking.exportStatement(accountId, st.from, st.to, st.category, st.direction))
                _state.update { it.copy(exportMessage = "Extrato exportado.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(exportMessage = "Não foi possível exportar: ${messageFor(e)}") }
            } finally {
                _state.update { it.copy(exporting = false) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value.statement
        if (s.hasMore && !s.loading && !s.loadingMore) loadStatement(reset = false)
    }

    fun retryStatement() = loadStatement(reset = _state.value.statement.items.isEmpty())

    private fun loadSummary() {
        _state.update { it.copy(summary = Load.Loading) }
        val month = _state.value.month
        viewModelScope.launch {
            val result = attempt { banking.statementSummary(accountId, month) }.toLoad()
            _state.update { if (it.month == month) it.copy(summary = result) else it }
        }
    }

    private fun loadHeader() {
        loadSummary()
        viewModelScope.launch {
            val account = attempt { banking.account(accountId) }
            _state.update { it.copy(account = account.toLoad(), refreshing = false) }
        }
        viewModelScope.launch {
            val limits = attempt { banking.limits(accountId) }
            _state.update { it.copy(limits = limits.toLoad()) }
        }
    }

    private fun loadStatement(reset: Boolean) {
        statementJob?.cancel() // um pedido novo (outro período) invalida o que ainda estava a caminho
        val before = _state.value.statement
        val page = if (reset) 0 else before.nextPage
        _state.update {
            it.copy(
                statement = if (reset) {
                    it.statement.copy(items = emptyList(), total = 0, nextPage = 0, loading = true, loadingMore = false, error = null)
                } else {
                    it.statement.copy(loadingMore = true, error = null)
                },
            )
        }
        statementJob = viewModelScope.launch {
            val result = attempt { banking.statement(accountId, page, from = before.from, to = before.to, category = before.category, direction = before.direction) }
            _state.update { current ->
                val s = current.statement
                current.copy(
                    statement = result.fold(
                        onSuccess = { p ->
                            s.copy(
                                items = if (reset) p.items else s.items + p.items,
                                total = p.totalElements,
                                nextPage = page + 1,
                                loading = false,
                                loadingMore = false,
                            )
                        },
                        onFailure = { e -> s.copy(loading = false, loadingMore = false, error = messageFor(e)) },
                    ),
                )
            }
        }
    }
}

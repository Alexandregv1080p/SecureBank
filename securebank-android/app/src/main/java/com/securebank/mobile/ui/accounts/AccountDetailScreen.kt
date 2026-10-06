package com.securebank.mobile.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.Transaction
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.Badge
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.theme.creditColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(viewModel: AccountDetailViewModel, onBack: () -> Unit, onDeposit: () -> Unit, onWithdraw: () -> Unit) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val statement = state.statement
    val context = LocalContext.current
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            viewModel.export { csv ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: error("não foi possível abrir o arquivo")
                }
            }
        }
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TextButton(onClick = onBack) { Text("‹ Contas") } }

            when (val account = state.account) {
                Load.Loading -> item { Skeleton(96.dp) }
                is Load.Failed -> item { ErrorState(account.message, onRetry = viewModel::refresh) }
                is Load.Ready -> item {
                    val a = account.value
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Format.accountTypeLabel(a.type), style = MaterialTheme.typography.headlineSmall)
                            if (a.status != "ACTIVE") Badge(if (a.status == "BLOCKED") "Conta bloqueada" else "Conta encerrada")
                        }
                        Text(Format.accountLabel(a.branch, a.accountNumber), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Saldo disponível", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        MoneyText(Money.format(a.balance.amount), style = MaterialTheme.typography.displaySmall)
                    }
                }
            }

            (state.account as? Load.Ready)?.value?.let { a ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onDeposit, enabled = a.status == "ACTIVE", modifier = Modifier.weight(1f)) { Text("Depositar") }
                        OutlinedButton(onClick = onWithdraw, enabled = a.status == "ACTIVE", modifier = Modifier.weight(1f)) { Text("Sacar") }
                    }
                }
            }

            item { Text("Limites de hoje", style = MaterialTheme.typography.titleMedium) }
            when (val limits = state.limits) {
                Load.Loading -> item { Skeleton(112.dp) }
                is Load.Failed -> item { Text("Não foi possível carregar os limites.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                is Load.Ready -> item {
                    Panel {
                        Column {
                            limits.value.forEachIndexed { i, l ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(Format.limitLabel(l.type), style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Até ${Money.format(l.perOperation.amount)} por operação",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        MoneyText(Money.format(l.remainingToday.amount), style = MaterialTheme.typography.titleSmall)
                                        Text("restante hoje", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Text("Resumo do mês", style = MaterialTheme.typography.titleMedium) }
            item { SummaryCard(state, onPrev = { viewModel.changeMonth(-1) }, onNext = { viewModel.changeMonth(1) }, onRetry = viewModel::refresh) }

            item { Text("Extrato", style = MaterialTheme.typography.titleMedium) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = statement.category == null, onClick = { viewModel.setCategory(null) }, label = { Text("Tudo") })
                        Format.categories.forEach { c ->
                            FilterChip(selected = statement.category == c, onClick = { viewModel.setCategory(c) }, label = { Text(Format.categoryLabel(c)) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = statement.direction == null, onClick = { viewModel.setDirection(null) }, label = { Text("Entradas e saídas") })
                        FilterChip(selected = statement.direction == "CREDIT", onClick = { viewModel.setDirection("CREDIT") }, label = { Text("Entradas") })
                        FilterChip(selected = statement.direction == "DEBIT", onClick = { viewModel.setDirection("DEBIT") }, label = { Text("Saídas") })
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DateButton("De", statement.from, Modifier.weight(1f)) { viewModel.setRange(it, statement.to) }
                        DateButton("Até", statement.to, Modifier.weight(1f)) { viewModel.setRange(statement.from, it) }
                    }
                    if (statement.from != null || statement.to != null) {
                        TextButton(onClick = { viewModel.setRange(null, null) }) { Text("Limpar período") }
                    }
                    OutlinedButton(onClick = { saveCsv.launch("extrato.csv") }, enabled = !state.exporting) {
                        Text(if (state.exporting) "Exportando…" else "Exportar CSV (período e filtros atuais)")
                    }
                    state.exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    statement.rangeError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }

            when {
                statement.loading -> item { Skeleton(192.dp) }
                statement.error != null && statement.items.isEmpty() -> item { ErrorState(statement.error, onRetry = viewModel::retryStatement) }
                statement.items.isEmpty() -> item { EmptyState("Nenhuma movimentação", "Não há lançamentos neste período.") }
                else -> {
                    item {
                        Panel {
                            Column {
                                statement.items.forEachIndexed { i, t ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    TransactionRow(t)
                                }
                            }
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            statement.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            if (statement.hasMore) {
                                OutlinedButton(onClick = viewModel::loadMore, enabled = !statement.loadingMore) {
                                    Text(if (statement.loadingMore) "Carregando…" else "Ver mais")
                                }
                            }
                            Text(
                                "${statement.items.size} de ${statement.total} lançamentos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: AccountDetailState, onPrev: () -> Unit, onNext: () -> Unit, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev) { Text("‹") }
            Text(Format.month(state.month), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onNext, enabled = state.canGoNext) { Text("›") }
        }
        when (val summary = state.summary) {
            Load.Loading -> Skeleton(112.dp)
            is Load.Failed -> ErrorState(summary.message, onRetry = onRetry)
            is Load.Ready -> Panel {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryLine("Entradas", summary.value.income.amount, credit = true)
                    SummaryLine("Saídas", summary.value.expenses.amount, credit = false)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SummaryLine("Resultado do mês", summary.value.net.amount, credit = null)
                    val active = summary.value.byCategory.filter { it.income.amount != "0.00" || it.expenses.amount != "0.00" }
                    if (active.isNotEmpty()) {
                        Text("Por categoria", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        active.forEach { c ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(Format.categoryLabel(c.category), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "+ ${Money.format(c.income.amount)}  − ${Money.format(c.expenses.amount)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, amount: String, credit: Boolean?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(
            Money.format(amount), style = MaterialTheme.typography.titleSmall,
            color = if (credit == true) creditColor() else androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}

@Composable
private fun TransactionRow(t: Transaction) {
    val credit = t.direction == "CREDIT"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (credit) creditColor().copy(alpha = 0.14f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (credit) "+" else "−", color = if (credit) creditColor() else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column {
                Text(Format.describe(t), style = MaterialTheme.typography.titleSmall)
                Text(Format.dateTime(t.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                "${if (credit) "+" else "−"} ${Money.format(t.amount.amount)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (credit) creditColor() else androidx.compose.ui.graphics.Color.Unspecified,
            )
            MoneyText("saldo ${Money.format(t.balanceAfter.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Botão que abre o seletor de data do Material. O seletor trabalha em UTC, então a conversão também. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(label: String, value: LocalDate?, modifier: Modifier, onChange: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(if (value != null) "$label: ${Format.date(value)}" else label)
    }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state = picker) }
    }
}

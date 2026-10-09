package com.securebank.mobile.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.core.util.Dashboard
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AreaChart
import com.securebank.mobile.ui.components.Badge
import com.securebank.mobile.ui.components.ChartSeries
import com.securebank.mobile.ui.components.Donut
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.HeroPanel
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.ProgressRing
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.components.Sparkline
import com.securebank.mobile.ui.piggy.PiggyRow
import com.securebank.mobile.ui.theme.Sb
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pt = Locale.forLanguageTag("pt-BR")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", pt)

/** Reais a partir de um Double calculado em centavos inteiros (só para mostrar). */
private fun reais(v: Double): String = Money.format(BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenAccount: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenPiggy: (String) -> Unit,
    onOpenPiggies: () -> Unit,
    onPix: () -> Unit,
    onTransfer: () -> Unit,
    onPay: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header(state.name, state.firstName) }
            when (val accounts = state.accounts) {
                Load.Loading -> item { Skeleton(220.dp) }
                is Load.Failed -> item { ErrorState(accounts.message, onRetry = viewModel::refresh) }
                is Load.Ready -> {
                    if (accounts.value.isEmpty()) {
                        item {
                            EmptyState("Você ainda não tem uma conta", "Abra uma conta corrente ou poupança para começar a movimentar.") {
                                PrimaryButton("Abrir conta", onClick = onOpenAccounts)
                            }
                        }
                    } else {
                        val balance = Money.sum(accounts.value.map { it.balance.amount })
                        item { QuickActions(onPix, onTransfer, onPay) }
                        item { BalanceCard(balance, accounts.value.size, state) }
                        item { MonthStats(state.overview, state.overviewLoading) }
                        item { Holdings(balance, state) }
                        item { ExpensesByCategory(state.overview, state.overviewLoading) }
                        item { RecentActivity(state.overview, state.overviewLoading) }
                        item { AccountList(accounts.value, onOpenAccount) }
                        item { PiggySection(state.piggies, onOpenPiggy, onOpenPiggies) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String?, firstName: String?) {
    val first = name?.let { Dashboard.titleCase(it).substringBefore(' ') } ?: firstName
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                dayFormat.format(LocalDate.now()).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(first?.let { "Olá, $it" } ?: "Olá", style = MaterialTheme.typography.headlineMedium)
        }
        name?.let {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text(Dashboard.initials(it), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** Os três caminhos mais usados, a um toque do saldo. */
@Composable
private fun QuickActions(onPix: () -> Unit, onTransfer: () -> Unit, onPay: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onPix, modifier = Modifier.weight(1f)) { Text("Pix") }
        OutlinedButton(onClick = onTransfer, modifier = Modifier.weight(1f)) { Text("Transferir") }
        OutlinedButton(onClick = onPay, modifier = Modifier.weight(1f)) { Text("Pagar") }
    }
}

@Composable
private fun BalanceCard(balance: String, accounts: Int, state: HomeState) {
    val sb = Sb.colors
    val overview = state.overview
    HeroPanel {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Saldo total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(Money.format(balance), style = MaterialTheme.typography.displaySmall)
            Text(
                "em $accounts ${if (accounts == 1) "conta" else "contas"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                state.overviewLoading -> Skeleton(180.dp, Modifier.padding(top = 12.dp))
                overview.hasMovement -> AreaChart(
                    labels = overview.months.map { Dashboard.monthShort(it.month) },
                    series = listOf(
                        ChartSeries("Entradas", sb.chart[1], overview.months.map { it.income }),
                        ChartSeries("Saídas", sb.chart[0], overview.months.map { it.expenses }),
                    ),
                    format = ::reais,
                    title = "Entradas e saídas dos últimos 6 meses",
                    modifier = Modifier.padding(top = 12.dp),
                )
                else -> Text(
                    "Sem movimentação nos últimos 6 meses. Quando você depositar, transferir ou pagar, o gráfico mostra as entradas e saídas mês a mês.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** Entradas, saídas e resultado do mês, lado a lado numa faixa que rola. */
@Composable
private fun MonthStats(overview: Overview, loading: Boolean) {
    val sb = Sb.colors
    val current = overview.current
    val previous = overview.previous
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading || current == null || previous == null) {
            repeat(3) { Skeleton(112.dp, Modifier.width(220.dp)) }
            return@Row
        }
        val net = current.income - current.expenses
        val savingRate = if (current.income > 0) maxOf(0.0, net / current.income * 100) else 0.0
        StatCard("Entradas do mês", reais(current.income), Dashboard.deltaPercent(current.income, previous.income), goodWhenUp = true) {
            Sparkline(overview.months.map { it.income }, sb.chart[1])
        }
        StatCard("Saídas do mês", reais(current.expenses), Dashboard.deltaPercent(current.expenses, previous.expenses), goodWhenUp = false) {
            Sparkline(overview.months.map { it.expenses }, sb.chart[0])
        }
        StatCard(
            "Resultado do mês",
            (if (net < 0) "−" else "") + reais(kotlin.math.abs(net)),
            delta = null,
            hint = if (current.income > 0) "${savingRate.toInt()}% da entrada sobrou" else "Sem entradas neste mês",
        ) {
            ProgressRing(savingRate.toFloat(), sb.chart[5], size = 44.dp) { Text("${savingRate.toInt()}%", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, delta: Double?, goodWhenUp: Boolean = true, hint: String? = null, visual: @Composable () -> Unit) {
    Panel(Modifier.width(236.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                visual()
            }
            MoneyText(value, style = MaterialTheme.typography.titleLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                DeltaText(delta, goodWhenUp)
            }
        }
    }
}

@Composable
private fun DeltaText(value: Double?, goodWhenUp: Boolean) {
    if (value == null) {
        Text("sem base de comparação", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val up = value >= 0
    val good = if (goodWhenUp) up else !up
    val color = if (good) Sb.colors.ok else MaterialTheme.colorScheme.error
    val pct = String.format(pt, "%.1f", kotlin.math.abs(value))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${if (up) "▲" else "▼"} $pct%", style = MaterialTheme.typography.bodySmall, color = color)
        Text("que o mês passado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Onde está o dinheiro: em conta, investido e nos porquinhos (e a moeda estrangeira, quando há). */
@Composable
private fun Holdings(balance: String, state: HomeState) {
    val sb = Sb.colors
    val invested = state.overview.invested
    val saved = Money.sum(state.piggies.map { it.balance.amount })
    val patrimony = Dashboard.total(balance, invested, saved)
    fun pct(v: String): Float = if (patrimony.signum() > 0) (BigDecimal(v).multiply(BigDecimal(100)).divide(patrimony, 4, RoundingMode.HALF_UP)).toFloat() else 0f
    Panel {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Onde está o seu dinheiro", style = MaterialTheme.typography.titleSmall)
                Text("Patrimônio de ${Money.format(patrimony.setScale(2, RoundingMode.HALF_UP).toPlainString())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HoldingRow("Em conta", balance, pct(balance), sb.chart[1])
            HoldingRow("Investido", invested, pct(invested), sb.chart[0])
            HoldingRow("Porquinhos", saved, pct(saved), sb.chart[2])
            val foreign = state.overview.wallets.filter { (it.balance.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO).signum() > 0 }
            if (foreign.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                foreign.forEach {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Moeda estrangeira · ${it.currency}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(com.securebank.mobile.core.util.FxValidation.format(it.currency, it.balance.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(label: String, value: String, percent: Float, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(percent, color, size = 48.dp) { Text("${percent.toInt()}%", style = MaterialTheme.typography.labelSmall) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            MoneyText(Money.format(BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpensesByCategory(overview: Overview, loading: Boolean) {
    val sb = Sb.colors
    Panel {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Saídas por categoria", style = MaterialTheme.typography.titleSmall)
                Text("Este mês", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                loading -> Skeleton(160.dp)
                overview.shares.isEmpty() -> Text("Nenhuma saída neste mês.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val total = overview.shares.sumOf { it.value }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(overview.shares.map { it.percent.toFloat() to sb.chart[it.colorIndex] }, "Saídas", reais(total))
                    }
                    overview.shares.forEach { s ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(sb.chart[s.colorIndex], CircleShape))
                                Text(s.label, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("${s.percent.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivity(overview: Overview, loading: Boolean) {
    Panel {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("Movimentações recentes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            when {
                loading -> Skeleton(180.dp, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                overview.recent.isEmpty() -> Text(
                    "Nenhuma movimentação ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                else -> overview.recent.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 20.dp))
                    val credit = r.transaction.direction == "CREDIT"
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (credit) "↙" else "↗", color = if (credit) Sb.colors.chart[1] else Sb.colors.chart[0], style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(Format.describe(r.transaction), style = MaterialTheme.typography.titleSmall)
                            Text("${r.accountLabel} · ${Format.dateTime(r.transaction.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MoneyText(
                            "${if (credit) "+" else "−"} ${Money.format(r.transaction.amount.amount)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (credit) Sb.colors.ok else Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountList(accounts: List<Account>, onOpenAccount: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suas contas", style = MaterialTheme.typography.titleMedium)
        Panel {
            Column {
                accounts.forEachIndexed { index, a ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    AccountRow(a, onClick = { onOpenAccount(a.id) })
                }
            }
        }
    }
}

@Composable
fun AccountRow(a: Account, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Format.accountTypeLabel(a.type), style = MaterialTheme.typography.titleSmall)
                if (a.status != "ACTIVE") {
                    Badge(if (a.status == "BLOCKED") "Bloqueada" else "Encerrada")
                }
            }
            Text(
                Format.accountLabel(a.branch, a.accountNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(Money.format(a.balance.amount), style = MaterialTheme.typography.titleSmall)
    }
}

/** Resumo dos porquinhos: total guardado, os 3 primeiros e o atalho para todos (e para criar o primeiro). */
@Composable
private fun PiggySection(piggies: List<Piggy>, onOpen: (String) -> Unit, onAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Porquinhos", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAll) { Text(if (piggies.isEmpty()) "Criar" else "Ver todos") }
        }
        if (piggies.isEmpty()) {
            Text(
                "Guarde dinheiro para um objetivo, separado do saldo da conta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Panel {
                Column {
                    piggies.take(3).forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        PiggyRow(p, onClick = { onOpen(p.id) })
                    }
                }
            }
            MoneyText(
                "Total guardado: " + Money.format(Money.sum(piggies.map { it.balance.amount })),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

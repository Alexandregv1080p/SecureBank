package com.securebank.mobile.ui.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.Investment
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.InvestmentValidation
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.theme.creditColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(viewModel: InvestmentsViewModel, onApply: (String) -> Unit, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value

    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = onBack) { Text("‹ Mais") } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Investimentos", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Renda fixa simulada. Você aplica a partir do saldo e resgata com o rendimento, já descontado o IR.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val inv = s.investments) {
                Load.Loading -> item { Skeleton(96.dp) }
                is Load.Failed -> item { ErrorState(inv.message, onRetry = viewModel::load) }
                is Load.Ready -> {
                    item {
                        Column {
                            Text("Total aplicado (líquido hoje)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyText(Money.format(s.totalNet), style = MaterialTheme.typography.displaySmall)
                        }
                    }
                    item { Text("Suas aplicações", style = MaterialTheme.typography.titleMedium) }
                    if (inv.value.isEmpty()) {
                        item { EmptyState("Nenhuma aplicação ainda", "Escolha um produto abaixo para começar.") }
                    } else {
                        item {
                            Panel {
                                Column {
                                    inv.value.forEachIndexed { i, it ->
                                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        InvestmentRow(it, onClick = { onOpen(it.id) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Text("Produtos", style = MaterialTheme.typography.titleMedium) }
            when (val products = s.products) {
                Load.Loading -> item { Skeleton(128.dp) }
                is Load.Failed -> item { ErrorState(products.message, onRetry = viewModel::load) }
                is Load.Ready -> item {
                    Panel {
                        Column {
                            products.value.forEachIndexed { i, p ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${InvestmentValidation.rateLabel(p.annualRatePercent)} · ${InvestmentValidation.termLabel(p.termDays)}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text("A partir de ${Money.format(p.minAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = { onApply(p.code) }) { Text("Aplicar") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestmentRow(i: Investment, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(i.productName, style = MaterialTheme.typography.titleSmall)
            Text(
                if (i.active) "Aplicado ${Money.format(i.principal.amount)} · ${i.daysHeld} dias" else "Resgatado",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(Money.format(i.net.amount), style = MaterialTheme.typography.titleSmall)
            if (i.active) MoneyText("+ ${Money.format(i.yield.amount)} bruto", style = MaterialTheme.typography.bodySmall, color = creditColor())
        }
    }
}

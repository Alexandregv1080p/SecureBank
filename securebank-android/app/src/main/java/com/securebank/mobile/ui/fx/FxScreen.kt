package com.securebank.mobile.ui.fx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.FxOperation
import com.securebank.mobile.core.network.FxRate
import com.securebank.mobile.core.network.FxWallet
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.FxValidation
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton

/** Câmbio: uma carteira por moeda, a cotação do dia (compra e venda), e as últimas operações. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxScreen(viewModel: FxViewModel, onBuy: (String) -> Unit, onSell: (String) -> Unit, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value

    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = onBack) { Text("‹ Mais") } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Câmbio", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Compre e venda dólar e euro (simulado). Você paga a cotação de compra e recebe a de venda; a diferença é o spread.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val rates = s.rates) {
                Load.Loading -> item { Skeleton(192.dp) }
                is Load.Failed -> item { ErrorState(rates.message, onRetry = viewModel::load) }
                is Load.Ready -> items(rates.value, key = { it.currency }) { rate ->
                    val wallet = (s.wallets as? Load.Ready)?.value?.firstOrNull { it.currency == rate.currency }
                    CurrencyCard(rate, wallet, onBuy = { onBuy(rate.currency) }, onSell = { onSell(rate.currency) })
                }
            }

            item { Text("Últimas operações", style = MaterialTheme.typography.titleMedium) }
            when (val ops = s.operations) {
                Load.Loading -> item { Skeleton(96.dp) }
                is Load.Failed -> item { ErrorState(ops.message, onRetry = viewModel::load) }
                is Load.Ready -> item {
                    if (ops.value.isEmpty()) {
                        Text("Você ainda não comprou nem vendeu moeda.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Panel {
                            Column {
                                ops.value.forEachIndexed { i, o ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    OperationRow(o)
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
private fun CurrencyCard(rate: FxRate, wallet: FxWallet?, onBuy: () -> Unit, onSell: () -> Unit) {
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(FxValidation.currencyName(rate.currency), style = MaterialTheme.typography.titleMedium)
            Text("Na carteira", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(FxValidation.format(rate.currency, wallet?.balance?.amount ?: "0.00"), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Compra R$ ${FxValidation.rateLabel(rate.buyRate)}", style = MaterialTheme.typography.bodyMedium)
                Text("Venda R$ ${FxValidation.rateLabel(rate.sellRate)}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onBuy, modifier = Modifier.weight(1f)) { Text("Comprar") }
                OutlinedButton(
                    onClick = onSell, modifier = Modifier.weight(1f),
                    enabled = (wallet?.balance?.amount?.toBigDecimalOrNull()?.signum() ?: 0) > 0,
                ) { Text("Vender") }
            }
        }
    }
}

@Composable
private fun OperationRow(o: FxOperation) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${FxValidation.sideLabel(o.side)} de ${FxValidation.format(o.foreignAmount.currency, o.foreignAmount.amount)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${Format.dateTime(o.createdAt)} · cotação R$ ${FxValidation.rateLabel(o.rate)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText("${if (o.bought) "−" else "+"} ${Money.format(o.brlAmount.amount)}", style = MaterialTheme.typography.titleSmall)
    }
}

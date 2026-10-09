package com.securebank.mobile.ui.piggy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.Skeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiggiesScreen(viewModel: PiggiesViewModel, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value

    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Porquinhos", style = MaterialTheme.typography.headlineSmall)
                    Text("Guarde dinheiro para seus objetivos, separado do saldo da conta.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (val piggies = s.piggies) {
                Load.Loading -> item { Skeleton(128.dp) }
                is Load.Failed -> item { ErrorState(piggies.message, onRetry = viewModel::refresh) }
                is Load.Ready -> {
                    if (piggies.value.isEmpty()) {
                        item {
                            EmptyState("Nenhum porquinho ainda", "Crie um para uma viagem, uma reserva de emergência ou qualquer meta.")
                        }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Total guardado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MoneyText(Money.format(Money.sum(piggies.value.map { it.balance.amount })), style = MaterialTheme.typography.displaySmall)
                            }
                        }
                        item {
                            Panel {
                                Column {
                                    piggies.value.forEachIndexed { i, p ->
                                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        PiggyRow(p, onClick = { onOpen(p.id) })
                                    }
                                }
                            }
                        }
                    }
                    item { PrimaryButton("Novo porquinho", onClick = onNew) }
                }
            }
        }
    }
}

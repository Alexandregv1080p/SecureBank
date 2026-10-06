package com.securebank.mobile.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.util.Format
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        state.firstName?.let { "Olá, $it" } ?: "Olá",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("Resumo das suas contas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (val accounts = state.accounts) {
                Load.Loading -> item { Skeleton(160.dp) }
                is Load.Failed -> item { ErrorState(accounts.message, onRetry = viewModel::refresh) }
                is Load.Ready -> {
                    if (accounts.value.isEmpty()) {
                        item {
                            EmptyState("Você ainda não tem uma conta", "Abra uma conta corrente ou poupança para começar a movimentar.") {
                                PrimaryButton("Abrir conta", onClick = onOpenAccounts)
                            }
                        }
                    } else {
                        item { TotalBalance(accounts.value) }
                        item { QuickActions(onPix, onTransfer, onPay) }
                        item { AccountList(accounts.value, onOpenAccount) }
                        item { PiggySection(state.piggies, onOpenPiggy, onOpenPiggies) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalBalance(accounts: List<Account>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Saldo total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(Money.format(Money.sum(accounts.map { it.balance.amount })), style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
private fun AccountList(accounts: List<Account>, onOpenAccount: (String) -> Unit) {
    Panel {
        Column {
            accounts.forEachIndexed { index, a ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                AccountRow(a, onClick = { onOpenAccount(a.id) })
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
                    com.securebank.mobile.ui.components.Badge(if (a.status == "BLOCKED") "Bloqueada" else "Encerrada")
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
private fun PiggySection(piggies: List<com.securebank.mobile.core.network.Piggy>, onOpen: (String) -> Unit, onAll: () -> Unit) {
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
                        com.securebank.mobile.ui.piggy.PiggyRow(p, onClick = { onOpen(p.id) })
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

/** Os três caminhos mais usados, a um toque do saldo. */
@Composable
private fun QuickActions(onPix: () -> Unit, onTransfer: () -> Unit, onPay: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Button(onClick = onPix, modifier = Modifier.weight(1f)) { Text("Pix") }
        androidx.compose.material3.OutlinedButton(onClick = onTransfer, modifier = Modifier.weight(1f)) { Text("Transferir") }
        androidx.compose.material3.OutlinedButton(onClick = onPay, modifier = Modifier.weight(1f)) { Text("Pagar") }
    }
}

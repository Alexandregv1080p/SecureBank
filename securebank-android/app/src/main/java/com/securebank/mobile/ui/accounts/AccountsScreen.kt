package com.securebank.mobile.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.Skeleton

@Composable
fun AccountsScreen(viewModel: AccountsViewModel, onOpenAccount: (String) -> Unit) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Contas", style = MaterialTheme.typography.headlineSmall)
                Text("Suas contas e saldos. Abra uma nova quando precisar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (val accounts = state.accounts) {
            Load.Loading -> item { Skeleton(128.dp) }
            is Load.Failed -> item { ErrorState(accounts.message, onRetry = viewModel::load) }
            is Load.Ready -> item {
                if (accounts.value.isEmpty()) {
                    EmptyState("Nenhuma conta aberta", "Use o formulário abaixo para abrir a primeira.")
                } else {
                    Panel {
                        Column {
                            accounts.value.forEachIndexed { i, a ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                AccountRow(a, onClick = { onOpenAccount(a.id) })
                            }
                        }
                    }
                }
            }
        }
        item {
            Panel {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Abrir nova conta", style = MaterialTheme.typography.titleMedium)
                    state.openError?.let { Banner(it) }
                    state.openedNumber?.let { Banner("Conta $it aberta.", isError = false) }
                    Text("Tipo de conta", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(selected = state.newType == "CHECKING", onClick = { viewModel.onType("CHECKING") }, label = { Text("Conta corrente") })
                        FilterChip(selected = state.newType == "SAVINGS", onClick = { viewModel.onType("SAVINGS") }, label = { Text("Poupança") })
                    }
                    PrimaryButton("Abrir conta", onClick = viewModel::open, loading = state.opening)
                }
            }
        }
    }
}

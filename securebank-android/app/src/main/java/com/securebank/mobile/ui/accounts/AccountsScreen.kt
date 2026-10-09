package com.securebank.mobile.ui.accounts

import com.securebank.mobile.ui.theme.creditColor
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.network.LimitUsage
import com.securebank.mobile.core.network.Account
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
        item { OpenAccountCard(state, viewModel, onOpenAccount) }
    }
}

private data class TypeOption(val type: String, val title: String, val text: String)

/** O que cada tipo é, sem prometer o que o banco não faz: hoje as duas contas funcionam do mesmo jeito. */
private val typeOptions = listOf(
    TypeOption("CHECKING", "Conta corrente", "Para o dia a dia: receber, transferir, pagar e usar o Pix."),
    TypeOption("SAVINGS", "Poupança", "Para separar o dinheiro do que você usa no dia a dia."),
)

private val limitNames = mapOf("WITHDRAW" to "Saque", "TRANSFER" to "Transferência", "PAYMENT" to "Pagamento", "PIX" to "Pix", "FX" to "Câmbio")

@Composable
private fun OpenAccountCard(state: AccountsState, viewModel: AccountsViewModel, onOpenAccount: (String) -> Unit) {
    val opened = state.opened
    if (opened != null) {
        OpenedAccount(opened, state.openedLimits, onSee = { onOpenAccount(opened.id) }, onAnother = viewModel::openAnother)
        return
    }
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Abrir nova conta", style = MaterialTheme.typography.titleMedium)
            state.openError?.let { Banner(it) }
            typeOptions.forEach { o ->
                val selected = state.newType == o.type
                Surface(
                    onClick = { viewModel.onType(o.type) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton; this.selected = selected },
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall)
                        Text(o.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Hoje as duas funcionam do mesmo jeito e começam com os mesmos limites. Para o dinheiro render, use os Investimentos. O número da conta é gerado ao abrir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Abrir ${Format.accountTypeLabel(state.newType).lowercase()}", onClick = viewModel::open, loading = state.opening)
        }
    }
}

@Composable
private fun OpenedAccount(account: Account, limits: Load<List<LimitUsage>>, onSee: () -> Unit, onAnother: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.size(40.dp))
                Text("${Format.accountTypeLabel(account.type)} aberta", style = MaterialTheme.typography.titleMedium)
            }
            ReceiptRow("Agência", account.branch)
            ReceiptRow("Conta", account.accountNumber)
            ReceiptRow("Saldo", Money.format(account.balance.amount))
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(Format.accountLabel(account.branch, account.accountNumber))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copiar agência e conta") }
            Text("Limites iniciais", style = MaterialTheme.typography.titleSmall)
            when (limits) {
                Load.Loading -> Skeleton(80.dp)
                is Load.Failed -> Text("Não foi possível carregar os limites agora.", style = MaterialTheme.typography.bodySmall)
                is Load.Ready -> limits.value.forEach { l ->
                    Column {
                        Text(limitNames[l.type] ?: l.type, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "até ${Money.format(l.perOperation.amount)} por vez, ${Money.format(l.daily.amount)} por dia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PrimaryButton("Ver conta", onClick = onSee)
            OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text("Abrir outra") }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBox
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

/** Igual ao teto do servidor (AccountApplicationService.MAX_OPEN_ACCOUNTS): contas encerradas não contam. */
const val MAX_OPEN_ACCOUNTS = 5

private fun openCount(accounts: Load<List<Account>>): Int? =
    (accounts as? Load.Ready)?.value?.count { it.status != "CLOSED" }

@Composable
private fun OpenAccountCard(state: AccountsState, viewModel: AccountsViewModel, onOpenAccount: (String) -> Unit) {
    val opened = state.opened
    val count = openCount(state.accounts)
    val full = (count ?: 0) >= MAX_OPEN_ACCOUNTS
    if (opened != null) {
        OpenedAccount(opened, state.openedLimits, count ?: 0, onSee = { onOpenAccount(opened.id) }, onAnother = viewModel::openAnother)
        return
    }
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Abrir nova conta", style = MaterialTheme.typography.titleMedium)
            state.openError?.let { Banner(it) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Contas abertas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (count == null) "…" else "$count de $MAX_OPEN_ACCOUNTS", style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { ((count ?: 0).toFloat() / MAX_OPEN_ACCOUNTS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                if (full) {
                    Text(
                        "Você chegou ao máximo de $MAX_OPEN_ACCOUNTS contas abertas. Encerre uma sem saldo para abrir outra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            typeOptions.forEach { o ->
                val selected = state.newType == o.type
                Surface(
                    onClick = { viewModel.onType(o.type) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton; this.selected = selected },
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (o.type == "CHECKING") Icons.Filled.AccountBox else Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(o.title, style = MaterialTheme.typography.titleSmall)
                            Text(o.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("O que acontece ao abrir", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "Agência 0001 e um número de conta novo, gerado agora.",
                        "Saldo inicial de ${Money.format("0")}. Você deposita depois, na própria conta.",
                        "Limites iniciais por operação e por dia, que você confere no resumo. Para o dinheiro render, use os Investimentos.",
                    ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            PrimaryButton(
                "Abrir ${Format.accountTypeLabel(state.newType).lowercase()}",
                onClick = viewModel::open,
                loading = state.opening,
                enabled = !full && count != null,
            )
        }
    }
}

@Composable
private fun OpenedAccount(account: Account, limits: Load<List<LimitUsage>>, count: Int, onSee: () -> Unit, onAnother: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.size(44.dp))
                Text("${Format.accountTypeLabel(account.type)} aberta", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ativa · aberta em ${Format.dateTime(account.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReceiptRow("Agência", account.branch)
                    ReceiptRow("Conta", account.accountNumber)
                    ReceiptRow("Saldo", Money.format(account.balance.amount))
                }
            }
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(Format.accountLabel(account.branch, account.accountNumber))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copiar agência e conta") }
            Text("Limites iniciais", style = MaterialTheme.typography.titleSmall)
            when (limits) {
                Load.Loading -> Skeleton(80.dp)
                is Load.Failed -> Text("Não foi possível carregar os limites agora. Eles ficam em Ver conta.", style = MaterialTheme.typography.bodySmall)
                is Load.Ready -> {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Operação", Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Por vez", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                        Text("Por dia", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    }
                    limits.value.forEach { l ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth()) {
                            Text(limitNames[l.type] ?: l.type, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(l.perOperation.amount), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                            Text(Money.format(l.daily.amount), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            Text("Próximo passo", style = MaterialTheme.typography.titleSmall)
            Text(
                "Abra a conta para depositar o primeiro saldo. Depois você pode criar uma chave Pix para receber e transferir para quem tem conta aqui.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Ver conta e depositar", onClick = onSee)
            OutlinedButton(onClick = onAnother, enabled = count < MAX_OPEN_ACCOUNTS, modifier = Modifier.fillMaxWidth()) { Text("Abrir outra") }
            Text(
                "$count de $MAX_OPEN_ACCOUNTS contas abertas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
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

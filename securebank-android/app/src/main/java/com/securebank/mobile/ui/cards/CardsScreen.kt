package com.securebank.mobile.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.util.CardValidation
import com.securebank.mobile.core.util.CreditValidation
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.Skeleton

/** Cartões de débito virtuais: a compra sai direto do saldo da conta, sem fatura. */
@Composable
fun CardsScreen(viewModel: CardsViewModel, onOpen: (String) -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(s.issuedId) {
        s.issuedId?.let {
            viewModel.consumeIssued()
            onOpen(it)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cartões", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Cartões virtuais de débito (a compra sai do saldo) e de crédito (limite, parcelas e fatura).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (val cards = s.cards) {
            Load.Loading -> item { Skeleton(180.dp) }
            is Load.Failed -> item { ErrorState(cards.message, onRetry = viewModel::load) }
            is Load.Ready -> {
                if (cards.value.isEmpty()) {
                    item { EmptyState("Você ainda não tem cartão", "Peça um cartão virtual abaixo, de débito ou de crédito. Você vê o número e o CVV na hora.") }
                }
                items(cards.value, key = { it.id }) { card ->
                    val account = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == card.accountId }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { onOpen(card.id) }) {
                        CardView(card)
                        Text(
                            listOfNotNull(
                                if (card.kind == "CREDIT") "Crédito" else "Débito",
                                CardValidation.statusLabel(card.status).takeIf { card.status != "ACTIVE" },
                                account?.let { Format.accountLabel(it.branch, it.accountNumber) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { NewCard(s, viewModel) }
    }
}

@Composable
private fun NewCard(s: CardsState, viewModel: CardsViewModel) {
    val accounts: List<Account> = (s.accounts as? Load.Ready)?.value.orEmpty().filter { it.status == "ACTIVE" }
    if (s.accounts is Load.Ready && accounts.isEmpty()) {
        EmptyState("Você precisa de uma conta ativa", "O cartão fica ligado a uma conta. Abra uma na aba Contas.")
        return
    }
    val selected = s.accountId ?: accounts.singleOrNull()?.id
    val full = selected != null && viewModel.openOn(selected) >= CardValidation.MAX_OPEN_PER_ACCOUNT
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pedir novo cartão", style = MaterialTheme.typography.titleMedium)
            s.error?.let { Banner(it) }
            val credit = s.kind == "CREDIT"
            listOf("DEBIT" to "Débito" to "A compra sai do saldo da conta na hora.", "CREDIT" to "Crédito" to "Limite inicial de ${CreditValidation.CREDIT_LIMIT_START}, parcele sem juros e pague a fatura depois.").forEach { (head, text) ->
                val (id, title) = head
                val selected = s.kind == id
                Surface(
                    onClick = { viewModel.onKind(id) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton; this.selected = selected },
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            listOf(
                "Número, validade e CVV aparecem na hora, só para você.",
                if (credit) "A compra usa o limite; o dinheiro sai da conta quando você paga a fatura. A fatura fecha 7 dias antes do vencimento." else "A compra sai do saldo da conta e respeita o limite do cartão.",
                "Até ${CardValidation.MAX_OPEN_PER_ACCOUNT} cartões de cada tipo por conta. Bloqueie e desbloqueie quando quiser.",
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (accounts.size > 1) AccountPicker("Conta do cartão", accounts, selected, viewModel::onAccount, s.accountError)
            if (credit) {
                Text("Dia do vencimento da fatura", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreditValidation.DUE_DAYS.forEach { d ->
                        androidx.compose.material3.FilterChip(selected = s.dueDay == d, onClick = { viewModel.onDueDay(d) }, label = { Text("$d") })
                    }
                }
            }
            if (full) Text("Esta conta já tem ${CardValidation.MAX_OPEN_PER_ACCOUNT} cartões de ${if (credit) "crédito" else "débito"}. Cancele um para pedir outro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            PrimaryButton(if (credit) "Pedir cartão de crédito" else "Pedir cartão de débito", onClick = viewModel::issue, loading = s.issuing, enabled = !full && s.accounts is Load.Ready)
        }
    }
}

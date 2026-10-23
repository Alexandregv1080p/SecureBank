package com.securebank.mobile.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
                    "Cartão de débito virtual: a compra sai direto do saldo da conta, sem fatura e sem juros.",
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
                    item { EmptyState("Você ainda não tem cartão", "Peça um cartão de débito virtual abaixo. Você vê o número e o CVV na hora.") }
                }
                items(cards.value, key = { it.id }) { card ->
                    val account = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == card.accountId }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { onOpen(card.id) }) {
                        CardView(card)
                        Text(
                            listOfNotNull(
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
            listOf(
                "Número, validade e CVV aparecem na hora, só para você.",
                "A compra sai do saldo da conta e respeita o limite do cartão.",
                "Até ${CardValidation.MAX_OPEN_PER_ACCOUNT} cartões por conta. Bloqueie e desbloqueie quando quiser.",
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (accounts.size > 1) AccountPicker("Conta do cartão", accounts, selected, viewModel::onAccount, s.accountError)
            if (full) Text("Esta conta já tem ${CardValidation.MAX_OPEN_PER_ACCOUNT} cartões. Cancele um para pedir outro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            PrimaryButton("Pedir cartão", onClick = viewModel::issue, loading = s.issuing, enabled = !full && s.accounts is Load.Ready)
        }
    }
}

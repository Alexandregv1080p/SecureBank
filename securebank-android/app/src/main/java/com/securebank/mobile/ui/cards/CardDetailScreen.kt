package com.securebank.mobile.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.util.CardValidation
import com.securebank.mobile.core.util.CreditValidation
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.Validation
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.rememberIdentityConfirmation

@Composable
fun CardDetailScreen(container: AppContainer, viewModel: CardDetailViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirmReveal = rememberIdentityConfirmation(container, "Confirme para ver o número e o CVV do cartão")
    val confirmPurchase = rememberIdentityConfirmation(container, "Confirme a compra no débito")
    var dialog by remember { mutableStateOf<CardAction?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val card = s.card) {
            Load.Loading -> Skeleton(200.dp)
            is Load.Failed -> ErrorState(card.message, onRetry = viewModel::load)
            is Load.Ready -> {
                val c = card.value
                val isCredit = c.kind == "CREDIT"
                CardView(c, s.details)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        listOfNotNull(CardValidation.statusLabel(c.status), s.account?.let { Format.accountLabel(it.branch, it.accountNumber) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (c.status != "ACTIVE" && !c.statusReason.isNullOrBlank()) {
                        Text(
                            "Motivo do ${if (c.status == "BLOCKED") "bloqueio" else "cancelamento"}: ${c.statusReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isCredit) {
                        CreditHero(s)
                    } else {
                    Text("Saldo da conta", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(s.account?.let { Money.format(it.balance.amount) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                    }
                    s.limit?.takeIf { !isCredit }?.let {
                        Text(
                            "Pode gastar hoje no cartão: ${Money.format(it.remainingToday.amount)} (até ${Money.format(it.perOperation.amount)} por compra)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                s.error?.let { Banner(it) }
                s.done?.let { Banner(it, isError = false) }

                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Dados do cartão", style = MaterialTheme.typography.titleMedium)
                        if (c.status == "CANCELED") {
                            Text("Cartão cancelado: os dados não ficam mais disponíveis.", style = MaterialTheme.typography.bodySmall)
                        } else if (s.details == null) {
                            Text(
                                "Número completo, validade e CVV aparecem só aqui, por $REVEAL_SECONDS segundos. Cada consulta fica registrada na sua conta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { confirmReveal { viewModel.reveal() } }, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text("Mostrar número e CVV") }
                        } else {
                            val d = s.details
                            DataRow("Número", CardValidation.formatNumber(d.number))
                            DataRow("Validade", CardValidation.formatExpiry(d.expiryMonth, d.expiryYear))
                            DataRow("CVV", d.cvv)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(d.number)) }, modifier = Modifier.weight(1f)) { Text("Copiar número") }
                                OutlinedButton(onClick = viewModel::hide, modifier = Modifier.weight(1f)) { Text("Ocultar") }
                            }
                            Text("Some em ${s.secondsLeft}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (isCredit) InvoicesPanel(s, viewModel)
                if (isCredit && c.status == "ACTIVE") LimitAndAdditionalPanels(s, viewModel) { run -> confirmPurchase(run) }

                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (isCredit) "Comprar no crédito (simulado)" else "Comprar no débito (simulado)", style = MaterialTheme.typography.titleMedium)
                        if (c.status != "ACTIVE") {
                            Text("Este cartão está ${CardValidation.statusLabel(c.status).lowercase()} e não aceita compras.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                if (isCredit) "Simula uma compra no crédito: usa o limite agora e entra na fatura. O dinheiro só sai da conta quando você paga a fatura."
                                else "Simula uma compra num estabelecimento: o valor sai do saldo da conta na hora.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SbTextField("Estabelecimento", s.merchant, viewModel::onMerchant, error = s.merchantError, hint = "Ex.: Mercado Central", enabled = !s.loading, maxLength = 60)
                            SbTextField(
                                "Valor (R$)", s.amount, viewModel::onAmount, error = s.amountError, mask = Validation::sanitizeAmount,
                                keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, enabled = !s.loading,
                            )
                            if (isCredit) InstallmentPicker(s, viewModel)
                            PrimaryButton(if (isCredit) "Comprar no crédito" else "Comprar", onClick = { confirmPurchase { viewModel.purchase() } }, loading = s.loading)
                        }
                    }
                }

                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Compras recentes", style = MaterialTheme.typography.titleMedium)
                        val rows = if (isCredit) s.creditPurchases.map { Triple(it.merchant, it.createdAt, it.total.amount) to ((if (it.installments > 1) "${it.installments}x de ${Money.format(it.installmentAmount.amount)}" else "à vista") + (if (it.madeWith != null) " · cartão adicional" else "")) }
                        else s.purchases.map { Triple(it.merchant, it.createdAt, it.amount.amount) to null }
                        if (rows.isEmpty()) {
                            Text("As compras feitas com este cartão aparecem aqui${if (isCredit) " e nas faturas" else " e no extrato"}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        rows.forEachIndexed { i, (p, extra) ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.first, style = MaterialTheme.typography.bodyMedium)
                                    Text(Format.dateTime(p.second) + (extra?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                MoneyText(Money.format(p.third), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Segurança", style = MaterialTheme.typography.titleMedium)
                        when (c.status) {
                            "ACTIVE" -> {
                                Text("Bloqueie se perdeu o controle do cartão. Você desbloqueia quando quiser.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { dialog = CardAction.BLOCK }, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text("Bloquear cartão") }
                            }
                            "BLOCKED" -> {
                                Text("O cartão está bloqueado: nenhuma compra passa até você desbloquear.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PrimaryButton("Desbloquear cartão", onClick = { viewModel.change(CardAction.UNBLOCK) }, loading = s.loading)
                            }
                            else -> Text("Este cartão foi cancelado e não volta a funcionar. Peça um novo na tela de cartões.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (c.status != "CANCELED") {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Text("Cancelar é definitivo: o cartão deixa de funcionar e não pode ser reativado. Para voltar a comprar, é só pedir um novo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { dialog = CardAction.CANCEL }, enabled = !s.loading) { Text("Cancelar cartão", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    val current = (s.card as? Load.Ready)?.value
    s.payTarget?.let { invoice -> PayInvoiceDialog(s, invoice, viewModel, onConfirm = { confirmPurchase { viewModel.payInvoice() } }) }
    s.splitTarget?.let { invoice -> SplitInvoiceDialog(s, invoice, viewModel, onConfirm = { confirmPurchase { viewModel.refinance() } }) }
    val action = dialog
    if (action != null && current != null) {
        CardActionDialog(
            card = current,
            action = action,
            busy = s.loading,
            onDismiss = { dialog = null },
            onConfirm = { reason ->
                dialog = null
                viewModel.change(action, reason)
            },
        )
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

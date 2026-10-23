package com.securebank.mobile.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.securebank.mobile.core.network.Invoice
import com.securebank.mobile.core.util.CreditValidation
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.Validation
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.SbTextField

/** Limite disponível, fatura do mês e quanto do limite já está em uso. */
@Composable
internal fun CreditHero(s: CardDetailState) {
    val summary = s.summary ?: return
    val limit = summary.creditLimit.amount.toDoubleOrNull() ?: 0.0
    val used = summary.used.amount.toDoubleOrNull() ?: 0.0
    val inv = summary.currentInvoice
    Text("Limite disponível", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MoneyText(Money.format(summary.available.amount), style = MaterialTheme.typography.headlineMedium)
    Text("de ${Money.format(summary.creditLimit.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(
        progress = { if (limit > 0) (used / limit).toFloat().coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = if (summary.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Text(
        "${Money.format(summary.used.amount)} em uso · vencimento todo dia ${summary.dueDay}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Fatura de ${CreditValidation.monthLabel(inv.month)}: ${Money.format(inv.remaining.amount)}. ${CreditValidation.dueHint(inv.status, inv.closingDate, inv.dueDate)}",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (summary.overdue) Banner("Há fatura vencida. Pague-a para voltar a comprar no crédito.")
}

/** Parcelas disponíveis para o valor digitado (respeitam a parcela mínima). */
@Composable
internal fun InstallmentPicker(s: CardDetailState, viewModel: CardDetailViewModel) {
    val options = CreditValidation.installmentOptions(s.amount)
    val current = options.firstOrNull { it.n == s.installments } ?: options.first()
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Parcelas", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text(current.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(o.label) }, onClick = { viewModel.onInstallments(o.n); open = false })
                }
            }
        }
    }
}

/** Faturas da mais próxima para a mais distante, com os itens e o botão de pagar. */
@Composable
internal fun InvoicesPanel(s: CardDetailState, viewModel: CardDetailViewModel) {
    val current = s.summary?.currentInvoice?.month
    val list = s.invoices.filter { it.total.amount.toDoubleOrNull() != 0.0 || it.month == current }.sortedBy { it.month }
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Faturas", style = MaterialTheme.typography.titleMedium)
            if (list.all { it.total.amount.toDoubleOrNull() == 0.0 }) {
                Text("As compras parceladas aparecem aqui, fatura por fatura.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list.forEachIndexed { i, inv ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                val future = current != null && inv.month > current
                val label = if (future) "Futura" else CreditValidation.invoiceStatusLabel(inv.status)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${CreditValidation.monthLabel(inv.month)} · $label", style = MaterialTheme.typography.bodyMedium)
                            Text(CreditValidation.dueHint(inv.status, inv.closingDate, inv.dueDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(Money.format(inv.total.amount), style = MaterialTheme.typography.bodyMedium)
                            if (inv.paid.amount.toDoubleOrNull() != 0.0) Text("pago ${Money.format(inv.paid.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.toggleInvoice(inv.month) }) { Text(if (s.openInvoice == inv.month) "Ocultar itens" else "Itens da fatura") }
                        if (inv.remaining.amount.toDoubleOrNull() != 0.0) OutlinedButton(onClick = { viewModel.openPay(inv) }, enabled = !s.loading) { Text("Pagar fatura") }
                    }
                    if (s.openInvoice == inv.month) {
                        val detail = s.invoiceDetails[inv.month]
                        if (detail == null) {
                            Text("Carregando…", style = MaterialTheme.typography.bodySmall)
                        } else {
                            if (detail.items.isEmpty()) Text("Nenhum item nesta fatura.", style = MaterialTheme.typography.bodySmall)
                            detail.items.forEach { it ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(it.merchant + if (it.installments > 1) " ${it.number}/${it.installments}" else "", style = MaterialTheme.typography.bodyMedium)
                                        Text("comprado em ${CreditValidation.shortDate(it.purchasedOn)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    MoneyText(Money.format(it.amount.amount), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            detail.payments.forEach {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pagamento em ${Format.dateTime(it.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    MoneyText(Money.format(it.amount.amount), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Janela de pagamento: a fatura inteira ou outro valor (o que sobrar continua na fatura). */
@Composable
internal fun PayInvoiceDialog(s: CardDetailState, invoice: Invoice, viewModel: CardDetailViewModel, onConfirm: () -> Unit) {
    val remaining = invoice.remaining.amount
    AlertDialog(
        onDismissRequest = { if (!s.loading) viewModel.closePay() },
        title = { Text("Pagar fatura de ${CreditValidation.monthLabel(invoice.month)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "O valor sai do saldo da conta${s.account?.let { " (${Money.format(it.balance.amount)} agora)" } ?: ""} e libera limite na hora. Vence em ${CreditValidation.shortDate(invoice.dueDate)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.error?.let { Banner(it) }
                listOf(true to "Fatura inteira, ${Money.format(remaining)}", false to "Outro valor").forEach { (all, label) ->
                    val selected = s.payAll == all
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !s.loading, role = Role.RadioButton) { viewModel.onPayAll(all) },
                    ) {
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null, enabled = !s.loading, modifier = Modifier.padding(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (!s.payAll) {
                    SbTextField(
                        "Valor (R$)", s.payAmount, viewModel::onPayAmount, error = s.payError, mask = Validation::sanitizeAmount,
                        hint = "O que sobrar continua na fatura. Vencida com saldo, ela bloqueia novas compras.",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, enabled = !s.loading,
                    )
                } else if (s.payError != null) {
                    Text(s.payError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { androidx.compose.material3.Button(onClick = onConfirm, enabled = !s.loading) { Text("Pagar") } },
        dismissButton = { TextButton(onClick = viewModel::closePay, enabled = !s.loading) { Text("Voltar") } },
    )
}

package com.securebank.mobile.ui.money

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.TransferField
import com.securebank.mobile.core.util.Validation
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.ReceiptLine
import com.securebank.mobile.ui.components.ReceiptPanel
import com.securebank.mobile.ui.components.ShareReceiptButton
import com.securebank.mobile.ui.components.receiptText
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.rememberIdentityConfirmation
import com.securebank.mobile.ui.theme.creditColor

@Composable
fun TransferScreen(container: AppContainer, viewModel: TransferViewModel, onOpenAccounts: () -> Unit, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirm = rememberIdentityConfirmation(container, "Confirme a transferência")
    var askLeave by remember { mutableStateOf(false) }
    val dirty = s.step != TransferStep.Sent && (s.number.isNotEmpty() || s.amount.isNotEmpty() || s.description.isNotEmpty())
    // voltar na revisão volta ao formulário; com algo digitado, pergunta antes de descartar
    BackHandler(enabled = s.step == TransferStep.Review || dirty) {
        if (s.step == TransferStep.Review) viewModel.backToForm() else askLeave = true
    }
    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("Descartar a transferência?") },
            text = { Text("O que você digitou será perdido.") },
            confirmButton = { TextButton(onClick = { askLeave = false; onBack() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Continuar") } },
        )
    }

    when {
        s.step == TransferStep.Sent && s.receipt != null -> SentReceipt(s.receipt, onNew = viewModel::newTransfer, onDone = onBack)
        else -> AuthScaffold("Transferir", "Envie dinheiro para outra conta do SecureBank pela agência e número da conta.") {
            when (val accounts = s.accounts) {
                Load.Loading -> Skeleton(192.dp)
                is Load.Failed -> ErrorState(accounts.message, onRetry = viewModel::loadAccounts)
                is Load.Ready -> when {
                    accounts.value.isEmpty() -> EmptyState("Você precisa de uma conta para transferir", "Abra uma conta primeiro.") {
                        PrimaryButton("Abrir conta", onClick = onOpenAccounts)
                    }
                    s.step == TransferStep.Review -> Review(s, accounts.value, onConfirm = { confirm { viewModel.confirm() } }, onEdit = viewModel::backToForm)
                    else -> Form(s, accounts.value, viewModel)
                }
            }
        }
    }
}

@Composable
private fun Form(s: TransferState, accounts: List<Account>, vm: TransferViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        s.error?.let { Banner(it) }
        AccountPicker("Conta de origem", accounts, s.sourceId, vm::onSource, s.errors[TransferField.Source])
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SbTextField("Agência", s.branch, vm::onBranch, modifier = Modifier.weight(1f), error = s.errors[TransferField.Branch], keyboardType = KeyboardType.Number, maxLength = 4)
            SbTextField("Conta de destino", s.number, vm::onNumber, modifier = Modifier.weight(2f), error = s.errors[TransferField.Number], keyboardType = KeyboardType.Number, hint = "Ex.: 123456-0 (o hífen entra sozinho)", mask = Validation::maskAccountNumber, maxLength = 14)
        }
        SbTextField("Valor (R$)", s.amount, vm::onAmount, error = s.errors[TransferField.Amount], keyboardType = KeyboardType.Decimal, hint = Validation.limitHint(s.limit), mask = Validation::sanitizeAmount)
        SbTextField("Descrição (opcional)", s.description, vm::onDescription, error = s.errors[TransferField.Description], imeAction = ImeAction.Done, onDone = vm::review, maxLength = 140)
        PrimaryButton("Revisar", onClick = vm::review, loading = s.loading)
    }
}

@Composable
private fun Review(s: TransferState, accounts: List<Account>, onConfirm: () -> Unit, onEdit: () -> Unit) {
    val source = accounts.firstOrNull { it.id == s.sourceId }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Confirme os dados", style = MaterialTheme.typography.titleMedium)
        Panel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ReviewRow("Valor") { MoneyText(Money.format(Money.parse(s.amount).orEmpty()), style = MaterialTheme.typography.titleLarge) }
                ReviewRow("De") { Text(source?.let { "${Format.accountTypeLabel(it.type)}, ${Format.accountLabel(it.branch, it.accountNumber)}" }.orEmpty(), style = MaterialTheme.typography.bodyMedium) }
                ReviewRow("Para") {
                    Column(horizontalAlignment = Alignment.End) {
                        s.destination?.let { d ->
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text("CPF ${d.document}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${d.bank}, ag. ${d.branch}, conta ${d.accountNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } ?: Text("Ag. ${s.branch}, conta ${s.number}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (s.description.isNotBlank()) ReviewRow("Descrição") { Text(s.description, style = MaterialTheme.typography.bodyMedium) }
                val value = Money.parse(s.amount)
                if (source != null && value != null) {
                    ReviewRow("Saldo depois") { MoneyText(Money.format(java.math.BigDecimal(source.balance.amount).subtract(java.math.BigDecimal(value)).toPlainString()), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Text("Confira o nome: depois de enviada, a transferência não pode ser desfeita por aqui.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.error?.let { Banner(it) }
        PrimaryButton("Confirmar transferência", onClick = onConfirm, loading = s.loading)
        OutlinedButton(onClick = onEdit, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text("Editar") }
    }
}

@Composable
private fun ReviewRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

@Composable
private fun SentReceipt(receipt: TransferReceipt, onNew: () -> Unit, onDone: () -> Unit) {
    val t = receipt.transfer
    val d = receipt.destination
    val lines = listOfNotNull(
        ReceiptLine("De", receipt.from),
        ReceiptLine("Para", d.name),
        ReceiptLine("CPF", d.document),
        ReceiptLine("Banco", "${d.bank}, ag. ${d.branch}, conta ${d.accountNumber}"),
        t.description?.let { ReceiptLine("Descrição", it) },
        ReceiptLine("Data", Format.dateTime(t.createdAt)),
        ReceiptLine("Identificador", t.id),
    )
    AuthScaffold("Transferir", null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ReceiptPanel("Transferência enviada", t.amount.amount, lines)
            PrimaryButton("Concluir", onClick = onDone)
            ShareReceiptButton(receiptText("Transferência enviada", t.amount.amount, lines))
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("Nova transferência") }
        }
    }
}

package com.securebank.mobile.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.rememberIdentityConfirmation
import com.securebank.mobile.ui.theme.creditColor

@Composable
fun TransferScreen(container: AppContainer, viewModel: TransferViewModel, onOpenAccounts: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirm = rememberIdentityConfirmation(container, "Confirme a transferência")

    when {
        s.step == TransferStep.Sent -> SentReceipt(s.sent.orEmpty(), onNew = viewModel::newTransfer)
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
        AccountPicker("Conta de origem", accounts, s.sourceId, vm::onSource, s.errors[TransferField.Source])
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SbTextField("Agência", s.branch, vm::onBranch, modifier = Modifier.weight(1f), error = s.errors[TransferField.Branch], keyboardType = KeyboardType.Number)
            SbTextField("Conta de destino", s.number, vm::onNumber, modifier = Modifier.weight(2f), error = s.errors[TransferField.Number], keyboardType = KeyboardType.Number, hint = "Formato 123456-7")
        }
        SbTextField("Valor (R$)", s.amount, vm::onAmount, error = s.errors[TransferField.Amount], keyboardType = KeyboardType.Decimal)
        SbTextField("Descrição (opcional)", s.description, vm::onDescription, error = s.errors[TransferField.Description], imeAction = ImeAction.Done, onDone = vm::review)
        PrimaryButton("Revisar", onClick = vm::review)
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
                ReviewRow("Para") { Text("Ag. ${s.branch}, conta ${s.number}", style = MaterialTheme.typography.bodyMedium) }
                if (s.description.isNotBlank()) ReviewRow("Descrição") { Text(s.description, style = MaterialTheme.typography.bodyMedium) }
            }
        }
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
private fun SentReceipt(summary: String, onNew: () -> Unit) {
    AuthScaffold("Transferência enviada", null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.padding(top = 16.dp).size(48.dp))
            Text(summary, style = MaterialTheme.typography.titleMedium)
            PrimaryButton("Nova transferência", onClick = onNew)
        }
    }
}

package com.securebank.mobile.ui.pix

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.PixField
import com.securebank.mobile.core.util.PixValidation
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
fun PixSendScreen(container: AppContainer, viewModel: PixSendViewModel, onHistory: () -> Unit, onOpenAccounts: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirm = rememberIdentityConfirmation(container, "Confirme o Pix")
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(viewModel::onScanned) }

    when (s.step) {
        PixSendStep.Sent -> Receipt(s, onNew = viewModel::newPix, onHistory = onHistory)
        PixSendStep.Key -> AuthScaffold("Enviar Pix", "Digite a chave (CPF, e-mail, celular ou aleatória) ou cole o Pix Copia e Cola.") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                s.error?.let { Banner(it) }
                SbTextField(
                    "Chave ou código", s.keyInput, viewModel::onKeyInput, error = s.keyError,
                    imeAction = ImeAction.Done, onDone = viewModel::continueWithKey, enabled = !s.loading,
                )
                PrimaryButton("Continuar", onClick = viewModel::continueWithKey, loading = s.loading)
                OutlinedButton(
                    onClick = {
                        scanner.launch(
                            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Aponte para o QR code do Pix")
                                .setBeepEnabled(false).setOrientationLocked(false),
                        )
                    },
                    enabled = !s.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ler QR code") }
                if (s.recents.isNotEmpty()) {
                    Text("Recentes", style = MaterialTheme.typography.titleMedium)
                    Panel {
                        Column {
                            s.recents.forEachIndexed { i, r ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Column(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !s.loading) { viewModel.useRecent(r) }.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(r.name, style = MaterialTheme.typography.titleSmall)
                                    Text(r.key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> AuthScaffold(if (s.step == PixSendStep.Review) "Confirme o Pix" else "Enviar Pix", null) {
            when (val accounts = s.accounts) {
                Load.Loading -> Skeleton(192.dp)
                is Load.Failed -> ErrorState(accounts.message, onRetry = {})
                is Load.Ready -> if (accounts.value.isEmpty()) {
                    EmptyState("Você precisa de uma conta para enviar Pix", "Abra uma conta primeiro.") { PrimaryButton("Abrir conta", onClick = onOpenAccounts) }
                } else if (s.step == PixSendStep.Details) {
                    Details(s, accounts.value, viewModel)
                } else {
                    Review(s, accounts.value, onConfirm = { confirm { viewModel.confirm() } }, onEdit = viewModel::backToDetails)
                }
            }
        }
    }
}

@Composable
private fun Recipient(s: PixSendState) {
    s.charge?.let { c ->
        Panel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cobrança para", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(c.receiverName, style = MaterialTheme.typography.titleMedium)
                Text("CPF ${c.receiverDocument} · ${c.bank}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("Vence em ${Format.dateTime(c.expiresAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val lookup = s.lookup ?: return
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Para", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(lookup.name, style = MaterialTheme.typography.titleMedium)
            Text("CPF ${lookup.document} · ${lookup.bank}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${com.securebank.mobile.core.util.PixValidation.typeLabel(lookup.type)}: ${lookup.key}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lookup.ownAccount) Text("Esta chave é sua.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Details(s: PixSendState, accounts: List<Account>, vm: PixSendViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        s.error?.let { Banner(it) }
        Recipient(s)
        TextButton(onClick = vm::backToKey) { Text("Trocar chave") }
        if (accounts.size > 1) AccountPicker("Enviar da conta", accounts, s.sourceId, vm::onSource, s.errors[PixField.Source])
        SbTextField(
            "Valor (R$)", s.amount, vm::onAmount, error = s.errors[PixField.Amount], keyboardType = KeyboardType.Decimal,
            hint = if (s.fixedAmount) "Valor definido pelo código" else null, enabled = !s.fixedAmount,
        )
        if (s.charge == null) {
            SbTextField("Mensagem (opcional)", s.message, vm::onMessage, error = s.errors[PixField.Message], imeAction = ImeAction.Done, onDone = vm::review)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agendar para outra data", style = MaterialTheme.typography.titleSmall)
                    Text("O dinheiro só sai na data escolhida.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.scheduleOn, onCheckedChange = vm::onSchedule)
            }
            if (s.scheduleOn) {
                SbTextField(
                    "Data (dd/mm/aaaa)", s.dateInput, vm::onDate, error = s.dateError, keyboardType = KeyboardType.Number,
                    hint = "De amanhã até ${PixValidation.MAX_SCHEDULE_DAYS} dias à frente", imeAction = ImeAction.Done, onDone = vm::review,
                )
            }
        }
        PrimaryButton("Revisar", onClick = vm::review)
    }
}

@Composable
private fun Review(s: PixSendState, accounts: List<Account>, onConfirm: () -> Unit, onEdit: () -> Unit) {
    val source = accounts.firstOrNull { it.id == s.sourceId }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Valor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(Money.format(Money.parse(s.amount).orEmpty()), style = MaterialTheme.typography.titleLarge)
                }
                Line("Para", s.charge?.receiverName ?: s.lookup?.name.orEmpty())
                if (s.charge == null) Line("Chave", s.resolvedKey) else Line("Cobrança", "paga na hora, uso único")
                if (s.scheduleOn && s.charge == null) Line("Data", s.dateInput.trim())
                Line("De", source?.let { "${Format.accountTypeLabel(it.type)}, ${Format.accountLabel(it.branch, it.accountNumber)}" }.orEmpty())
                if (s.message.isNotBlank()) Line("Mensagem", s.message.trim())
            }
        }
        s.error?.let { Banner(it) }
        PrimaryButton(if (s.scheduleOn && s.charge == null) "Confirmar agendamento" else "Confirmar Pix", onClick = onConfirm, loading = s.loading)
        OutlinedButton(onClick = onEdit, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text("Editar") }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun Receipt(s: PixSendState, onNew: () -> Unit, onHistory: () -> Unit) {
    val entry = s.receipt
    AuthScaffold(if (s.scheduled != null) "Pix agendado" else "Pix enviado", null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.padding(top = 16.dp).size(48.dp))
            if (entry != null) {
                MoneyText(Money.format(entry.amount.amount), style = MaterialTheme.typography.displaySmall)
                Text("para ${entry.counterpartName}", style = MaterialTheme.typography.titleMedium)
                entry.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("Identificador", style = MaterialTheme.typography.labelLarge)
                Text(entry.endToEndId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.scheduled?.let { sch ->
                MoneyText(Money.format(sch.amount.amount), style = MaterialTheme.typography.displaySmall)
                Text("para ${sch.destinationName}", style = MaterialTheme.typography.titleMedium)
                Text("em ${PixValidation.displayDate(sch.scheduledFor)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Se na data o saldo ou o limite não cobrirem, o Pix não é feito e você é avisado. Você pode cancelar em Agendados até lá.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimaryButton("Novo Pix", onClick = onNew)
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Ver histórico") }
        }
    }
}

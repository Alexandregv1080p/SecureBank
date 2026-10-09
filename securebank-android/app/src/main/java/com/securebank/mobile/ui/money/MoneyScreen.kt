package com.securebank.mobile.ui.money

import androidx.compose.ui.Modifier
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.rememberIdentityConfirmation

private fun withdrawHint(s: MoneyState): String? {
    val parts = listOfNotNull(
        s.balance?.let { "Saldo ${com.securebank.mobile.core.util.Money.format(it)}" },
        s.limit?.let { "por operação até ${com.securebank.mobile.core.util.Money.format(it.perOperation.amount)}, restam ${com.securebank.mobile.core.util.Money.format(it.remainingToday.amount)} hoje" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Depósito (entra dinheiro: sem biometria) ou saque (sai dinheiro: pede a identidade). */
@Composable
fun MoneyScreen(container: AppContainer, viewModel: MoneyViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val withdraw = viewModel.kind == MoneyKind.WITHDRAW
    val confirm = rememberIdentityConfirmation(container, "Confirme o saque")
    val submit = { if (withdraw) confirm { viewModel.submit() } else viewModel.submit() }

    val receipt = s.receipt
    if (receipt != null) {
        val title = if (withdraw) "Saque realizado" else "Depósito realizado"
        val lines = listOfNotNull(
            s.accountLabel?.let { com.securebank.mobile.ui.components.ReceiptLine("Conta", it) },
            com.securebank.mobile.ui.components.ReceiptLine("Saldo depois", com.securebank.mobile.core.util.Money.format(receipt.balanceAfter.amount)),
            com.securebank.mobile.ui.components.ReceiptLine("Data", com.securebank.mobile.core.util.Format.dateTime(receipt.createdAt)),
            com.securebank.mobile.ui.components.ReceiptLine("Identificador", receipt.id),
        )
        AuthScaffold(title = if (withdraw) "Sacar" else "Depositar", subtitle = null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.securebank.mobile.ui.components.ReceiptPanel(title, receipt.amount.amount, lines)
                PrimaryButton("Concluir", onClick = onBack)
                com.securebank.mobile.ui.components.ShareReceiptButton(com.securebank.mobile.ui.components.receiptText(title, receipt.amount.amount, lines))
                OutlinedButton(onClick = viewModel::newOperation, modifier = Modifier.fillMaxWidth()) { Text(if (withdraw) "Novo saque" else "Novo depósito") }
            }
        }
        return
    }

    AuthScaffold(
        title = if (withdraw) "Sacar" else "Depositar",
        subtitle = if (withdraw) "Retire dinheiro da conta, dentro do seu limite de saque." else "Adicione dinheiro à conta (depósito simulado).",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            s.error?.let { Banner(it) }
            SbTextField(
                label = "Valor (R$)",
                value = s.amount,
                onValueChange = viewModel::onAmount,
                error = s.amountError,
                hint = if (withdraw) withdrawHint(s) else null,
                keyboardType = KeyboardType.Decimal,
                mask = com.securebank.mobile.core.util.Validation::sanitizeAmount,
                imeAction = ImeAction.Done,
                onDone = { submit() },
                enabled = !s.loading,
            )
            PrimaryButton(if (withdraw) "Sacar" else "Depositar", onClick = { submit() }, loading = s.loading)
            TextButton(onClick = onBack) { Text("Voltar") }
        }
    }
}

package com.securebank.mobile.ui.fx

import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.util.FxValidation
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.rememberIdentityConfirmation
import com.securebank.mobile.ui.theme.creditColor

@Composable
fun FxTradeScreen(container: AppContainer, viewModel: FxTradeViewModel, currency: String, onDone: () -> Unit, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val buying = viewModel.buying
    val confirm = rememberIdentityConfirmation(container, if (buying) "Confirme a compra" else "Confirme a venda")
    val name = FxValidation.currencyName(currency)

    s.done?.let { op ->
        AuthScaffold(if (buying) "Compra realizada" else "Venda realizada", null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.padding(top = 16.dp).size(48.dp))
                MoneyText(FxValidation.format(currency, op.foreignAmount.amount), style = MaterialTheme.typography.displaySmall)
                Text(
                    if (buying) "por ${Money.format(op.brlAmount.amount)}" else "você recebeu ${Money.format(op.brlAmount.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Cotação aplicada: R$ ${FxValidation.rateLabel(op.rate)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton("Concluir", onClick = onDone)
            }
        }
        return
    }

    AuthScaffold(if (buying) "Comprar $name" else "Vender $name", null) {
        when (val rate = s.rate) {
            Load.Loading -> Skeleton(160.dp)
            is Load.Failed -> ErrorState(rate.message, onRetry = viewModel::load)
            is Load.Ready -> if (s.step == FxStep.Review) {
                Review(s, viewModel, currency, rate.value, onConfirm = { confirm { viewModel.confirm() } })
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val r = rate.value
                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (buying) "Cotação de compra" else "Cotação de venda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("1 $currency = R$ ${FxValidation.rateLabel(if (buying) r.buyRate else r.sellRate)}", style = MaterialTheme.typography.titleMedium)
                        Text("Comercial R$ ${FxValidation.rateLabel(r.mid)} · spread de ${r.spreadPercent.replace('.', ',')}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!buying) Text("Na carteira: ${FxValidation.format(currency, s.walletBalance)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                when (val accounts = s.accounts) {
                    Load.Loading -> Skeleton(96.dp)
                    is Load.Failed -> ErrorState(accounts.message, onRetry = viewModel::load)
                    is Load.Ready -> {
                        if (accounts.value.size > 1) AccountPicker(if (buying) "Pagar com a conta" else "Receber na conta", accounts.value, s.accountId, viewModel::onAccount, null)
                        SbTextField(
                            "Quantidade ($currency)", s.amount, viewModel::onAmount, error = s.amountError,
                            keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, enabled = !s.loading,
                            mask = com.securebank.mobile.core.util.Validation::sanitizeAmount,
                        )
                        if (!buying) TextButton(onClick = viewModel::sellAll) { Text("Vender tudo") }
                    }
                }
                viewModel.estimate(s)?.let {
                    Text(
                        if (buying) "Você paga ${Money.format(it)}" else "Você recebe ${Money.format(it)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                s.error?.let { Banner(it) }
                PrimaryButton("Revisar", onClick = viewModel::review, loading = s.loading)
                TextButton(onClick = onBack, enabled = !s.loading) { Text("Cancelar") }
            }
        }
    }
}

/** Revisão: cotação conferida de novo, total, saldo e carteira depois; a cotação vale [QUOTE_SECONDS] s e depois é conferida outra vez. */
@Composable
private fun Review(s: FxTradeState, vm: FxTradeViewModel, currency: String, rate: com.securebank.mobile.core.network.FxRate, onConfirm: () -> Unit) {
    val buying = vm.buying
    val value = Money.parse(s.amount)
    val brl = vm.estimate(s)
    val account = (s.accounts as? Load.Ready)?.value?.firstOrNull { it.id == s.accountId }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Confirme a operação", style = MaterialTheme.typography.titleMedium)
        s.notice?.let { Banner(it, isError = false) }
        Panel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (value != null) FxReviewRow(if (buying) "Você compra" else "Você vende") { MoneyText(FxValidation.format(currency, value), style = MaterialTheme.typography.titleLarge) }
                FxReviewRow("Cotação") {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("R$ ${FxValidation.rateLabel(s.reviewRate.orEmpty())} por $currency", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "comercial R$ ${FxValidation.rateLabel(rate.mid)} · spread de ${rate.spreadPercent.replace('.', ',')}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (brl != null) FxReviewRow(if (buying) "Você paga" else "Você recebe") { MoneyText(Money.format(brl), style = MaterialTheme.typography.titleLarge) }
                if (account != null) {
                    FxReviewRow(if (buying) "Sai da conta" else "Entra na conta") { Text(account.accountNumber, style = MaterialTheme.typography.bodyMedium) }
                    if (brl != null) {
                        val before = java.math.BigDecimal(account.balance.amount)
                        val after = if (buying) before.subtract(java.math.BigDecimal(brl)) else before.add(java.math.BigDecimal(brl))
                        FxReviewRow("Saldo depois") { MoneyText(Money.format(after.toPlainString()), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (value != null) {
                    val wallet = java.math.BigDecimal(s.walletBalance)
                    val after = if (buying) wallet.add(java.math.BigDecimal(value)) else wallet.subtract(java.math.BigDecimal(value))
                    FxReviewRow("Carteira depois") { MoneyText(FxValidation.format(currency, after.toPlainString()), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Text(
            "Cotação válida por mais ${s.secondsLeft} s; depois conferimos de novo. Se ela mudar antes de você confirmar, a operação é recusada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.error?.let { Banner(it) }
        PrimaryButton(if (buying) "Confirmar compra" else "Confirmar venda", onClick = onConfirm, loading = s.loading)
        OutlinedButton(onClick = vm::backToForm, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text("Editar") }
    }
}

@Composable
private fun FxReviewRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

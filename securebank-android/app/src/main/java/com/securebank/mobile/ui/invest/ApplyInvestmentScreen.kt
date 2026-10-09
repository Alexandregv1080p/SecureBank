package com.securebank.mobile.ui.invest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.InvestmentValidation
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
fun ApplyInvestmentScreen(container: AppContainer, viewModel: ApplyInvestmentViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirm = rememberIdentityConfirmation(container, "Confirme a aplicação")

    s.done?.let { i ->
        AuthScaffold("Aplicação realizada", null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.padding(top = 16.dp).size(48.dp))
                MoneyText(Money.format(i.principal.amount), style = MaterialTheme.typography.displaySmall)
                Text(i.productName, style = MaterialTheme.typography.titleMedium)
                i.maturesAt?.let { Text("Disponível para resgate em ${Format.dateTime(it)}", style = MaterialTheme.typography.bodyMedium) }
                PrimaryButton("Ver aplicação", onClick = { onOpen(i.id) })
            }
        }
        return
    }

    AuthScaffold("Aplicar", null) {
        when (val product = s.product) {
            Load.Loading -> Skeleton(160.dp)
            is Load.Failed -> ErrorState(product.message, onRetry = viewModel::load)
            is Load.Ready -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val p = product.value
                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${InvestmentValidation.rateLabel(p.annualRatePercent)} · ${InvestmentValidation.termLabel(p.termDays)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Mínimo ${Money.format(p.minAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (p.termDays == null) "Resgate quando quiser. O rendimento conta por dia completo."
                            else "Só resgata no vencimento; o rendimento para nessa data.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "IR regressivo sobre o rendimento: 22,5% até 180 dias, 20% até 360, 17,5% até 720 e 15% acima.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (val accounts = s.accounts) {
                    Load.Loading -> Skeleton(96.dp)
                    is Load.Failed -> ErrorState(accounts.message, onRetry = viewModel::load)
                    is Load.Ready -> {
                        if (accounts.value.size > 1) AccountPicker("Aplicar a partir da conta", accounts.value, s.accountId, viewModel::onAccount, null)
                        SbTextField(
                            "Valor (R$)", s.amount, viewModel::onAmount, error = s.amountError, mask = com.securebank.mobile.core.util.Validation::sanitizeAmount, keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done, enabled = !s.loading,
                        )
                    }
                }
                s.error?.let { Banner(it) }
                PrimaryButton("Aplicar", onClick = { if (viewModel.validate()) confirm { viewModel.confirm() } }, loading = s.loading)
                TextButton(onClick = onBack, enabled = !s.loading) { Text("Cancelar") }
            }
        }
    }
}

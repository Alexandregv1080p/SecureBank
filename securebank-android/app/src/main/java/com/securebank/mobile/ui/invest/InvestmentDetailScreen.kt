package com.securebank.mobile.ui.invest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.network.Investment
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.InvestmentValidation
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.rememberIdentityConfirmation

@Composable
fun InvestmentDetailScreen(container: AppContainer, viewModel: InvestmentDetailViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirm = rememberIdentityConfirmation(container, "Confirme o resgate")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Investimentos") }
        when (val inv = s.investment) {
            Load.Loading -> Skeleton(192.dp)
            is Load.Failed -> ErrorState(inv.message, onRetry = viewModel::load)
            is Load.Ready -> {
                val i = inv.value
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(i.productName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${InvestmentValidation.rateLabel(i.annualRatePercent)} · ${InvestmentValidation.termLabel(i.termDays)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(if (i.active) "Líquido se resgatar agora" else "Líquido resgatado", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    MoneyText(Money.format(i.net.amount), style = MaterialTheme.typography.displaySmall)
                }
                Values(i)
                s.error?.let { Banner(it) }
                if (i.active) {
                    if (i.canRedeem) {
                        PrimaryButton("Resgatar tudo", onClick = { confirm { viewModel.redeem() } }, loading = s.loading)
                    } else {
                        Text(
                            "Este investimento só pode ser resgatado no vencimento: ${i.maturesAt?.let { Format.dateTime(it) }.orEmpty()}.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Values(i: Investment) {
    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Line("Aplicado", Money.format(i.principal.amount))
            Line("Aplicado em", Format.dateTime(i.appliedAt))
            Line("Dias rendendo", "${i.daysHeld}")
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Line("Valor bruto", Money.format(i.gross.amount))
            Line("Rendimento", Money.format(i.yield.amount))
            Line("IR (${i.taxRatePercent.replace('.', ',')}%)", "− ${Money.format(i.tax.amount)}")
            Line("Líquido", Money.format(i.net.amount))
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

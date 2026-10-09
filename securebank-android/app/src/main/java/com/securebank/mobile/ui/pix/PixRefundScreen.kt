package com.securebank.mobile.ui.pix

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
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
fun PixRefundScreen(container: AppContainer, viewModel: PixRefundViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val confirm = rememberIdentityConfirmation(container, "Confirme a devolução")

    s.done?.let { refund ->
        AuthScaffold("Devolução enviada", null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.padding(top = 16.dp).size(48.dp))
                MoneyText(Money.format(refund.amount.amount), style = MaterialTheme.typography.displaySmall)
                Text("para ${refund.counterpartName}", style = MaterialTheme.typography.titleMedium)
                PrimaryButton("Voltar ao histórico", onClick = onBack)
            }
        }
        return
    }

    AuthScaffold("Devolver Pix", "Devolva tudo ou parte do valor. O prazo é de 90 dias.") {
        when (val o = s.original) {
            Load.Loading -> Skeleton(160.dp)
            is Load.Failed -> ErrorState(o.message, onRetry = viewModel::load)
            is Load.Ready -> if (!o.value.canRefund) {
                EmptyState("Este Pix não pode mais ser devolvido", "O prazo acabou, o valor já foi devolvido ou ele não é um Pix recebido.") {
                    OutlinedButton(onClick = onBack) { Text("Voltar") }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Pix recebido de", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(o.value.counterpartName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Money.format(o.value.amount.amount)} em ${Format.dateTime(o.value.createdAt)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Ainda pode devolver ${Money.format(o.value.refundableAmount!!.amount)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    SbTextField(
                        "Valor a devolver (R$)", s.amount, viewModel::onAmount, error = s.amountError, mask = com.securebank.mobile.core.util.Validation::sanitizeAmount,
                        keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, enabled = !s.loading,
                    )
                    s.error?.let { Banner(it) }
                    PrimaryButton("Devolver", onClick = { confirm { viewModel.confirm() } }, loading = s.loading)
                    OutlinedButton(onClick = onBack, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                }
            }
        }
    }
}

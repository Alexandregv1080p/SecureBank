package com.securebank.mobile.ui.pix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.PixCharge
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.core.util.PixValidation
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton

@Composable
fun PixChargesScreen(viewModel: PixChargesViewModel) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Cobrar", style = MaterialTheme.typography.headlineSmall)
            Text("Crie uma cobrança de valor fixo. O QR vale uma vez só e até a validade escolhida.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.error?.let { Banner(it) }

        val shown = s.shown
        val code = s.shownCode
        if (shown != null && code != null) {
            Text("${Money.format(shown.amount.amount)}${shown.description?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.titleMedium)
            QrImage(code)
            Text("Vale até ${Format.dateTime(shown.expiresAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton("Copiar código", onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pix Copia e Cola", code))
                Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
            })
            OutlinedButton(onClick = viewModel::hide, modifier = Modifier.fillMaxWidth()) { Text("Fechar QR") }
        } else {
            when (val accounts = s.accounts) {
                Load.Loading -> Skeleton(160.dp)
                is Load.Failed -> ErrorState(accounts.message, onRetry = viewModel::load)
                is Load.Ready -> {
                    if (accounts.value.size > 1) AccountPicker("Receber na conta", accounts.value, s.sourceId, viewModel::onSource, null)
                    SbTextField("Valor (R$)", s.amount, viewModel::onAmount, error = s.amountError, keyboardType = KeyboardType.Decimal)
                    SbTextField("Descrição (opcional)", s.description, viewModel::onDescription, error = s.descriptionError, imeAction = ImeAction.Done, onDone = viewModel::create)
                    Text("Validade", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChargeValidity.entries.forEach { v ->
                            FilterChip(selected = s.validity == v, onClick = { viewModel.onValidity(v) }, label = { Text(v.label) })
                        }
                    }
                    PrimaryButton("Criar cobrança", onClick = viewModel::create, loading = s.creating)
                }
            }
        }

        Text("Suas cobranças", style = MaterialTheme.typography.titleMedium)
        when (val charges = s.charges) {
            Load.Loading -> Skeleton(96.dp)
            is Load.Failed -> ErrorState(charges.message, onRetry = viewModel::load)
            is Load.Ready -> if (charges.value.isEmpty()) {
                Text("Nenhuma cobrança criada ainda.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Panel {
                    Column {
                        charges.value.forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            ChargeRow(c, onShow = { viewModel.show(c) }, onCancel = { viewModel.cancel(c) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeRow(c: PixCharge, onShow: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(c.description ?: "Cobrança", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            MoneyText(Money.format(c.amount.amount), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "${PixValidation.chargeStatusLabel(c.status)} · ${Format.dateTime(if (c.status == "PAID") c.paidAt ?: c.createdAt else c.expiresAt)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (c.status == "ACTIVE") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShow) { Text("Mostrar QR") }
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

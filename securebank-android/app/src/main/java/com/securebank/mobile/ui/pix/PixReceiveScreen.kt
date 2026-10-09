package com.securebank.mobile.ui.pix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.util.PixValidation
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton

@Composable
fun PixReceiveScreen(viewModel: PixReceiveViewModel, onKeys: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Receber Pix", style = MaterialTheme.typography.headlineSmall)
            Text("Mostre o QR code ou envie o código. O valor é opcional.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (val keys = s.keys) {
            Load.Loading -> Skeleton(192.dp)
            is Load.Failed -> ErrorState(keys.message, onRetry = viewModel::load)
            is Load.Ready -> if (keys.value.isEmpty()) {
                EmptyState("Cadastre uma chave para receber", "Sem chave não há para onde o dinheiro ir.") {
                    PrimaryButton("Cadastrar chave", onClick = onKeys)
                }
            } else {
                if (keys.value.size > 1) {
                    Text("Receber na chave", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.value.forEach { k ->
                            FilterChip(
                                selected = k.id == s.selectedKeyId,
                                onClick = { viewModel.onKey(k.id) },
                                label = { Text("${PixValidation.typeLabel(k.type)}: ${k.key}", maxLines = 1) },
                            )
                        }
                    }
                } else {
                    Text("${PixValidation.typeLabel(keys.value[0].type)}: ${keys.value[0].key}", style = MaterialTheme.typography.titleSmall)
                }
                SbTextField(
                    "Valor (opcional)", s.amount, viewModel::onAmount, error = s.amountError,
                    keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, hint = "Em branco: quem paga digita o valor",
                )
                s.code?.let { code ->
                    QrImage(code)
                    Text("Pix Copia e Cola", style = MaterialTheme.typography.labelLarge)
                    Text(code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton("Copiar código", onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Pix Copia e Cola", code))
                        Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}

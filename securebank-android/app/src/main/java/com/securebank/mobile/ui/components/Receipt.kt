package com.securebank.mobile.ui.components

import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.theme.creditColor

/** Uma linha do comprovante: rótulo e valor. */
data class ReceiptLine(val label: String, val value: String)

/** Comprovante na tela: marca do banco, valor em destaque e as linhas (quem, quando, identificador). */
@Composable
fun ReceiptPanel(title: String, amount: String, lines: List<ReceiptLine>) {
    Panel {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SecureBank", style = MaterialTheme.typography.titleSmall)
                Text("Comprovante", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = creditColor(), modifier = Modifier.size(40.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                MoneyText(Money.format(amount), style = MaterialTheme.typography.headlineMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(l.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(l.value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** O comprovante como texto simples, para mandar por qualquer app (mensagem, e-mail, notas). */
fun receiptText(title: String, amount: String, lines: List<ReceiptLine>): String =
    buildString {
        appendLine("SecureBank · $title")
        appendLine(Money.format(amount))
        lines.forEach { appendLine("${it.label}: ${it.value}") }
    }.trimEnd()

@Composable
fun ShareReceiptButton(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Compartilhar comprovante"))
        },
        modifier = modifier.fillMaxWidth(),
    ) { Text("Compartilhar comprovante") }
}

package com.securebank.mobile.ui.pix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.securebank.mobile.core.network.PixEntry
import com.securebank.mobile.core.pix.QrCode
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.theme.creditColor

private fun entryTitle(e: PixEntry): String = when {
    e.isRefund && e.sent -> "Devolução enviada para ${e.counterpartName}"
    e.isRefund -> "Devolução recebida de ${e.counterpartName}"
    e.sent -> "Pix enviado para ${e.counterpartName}"
    else -> "Pix recebido de ${e.counterpartName}"
}

/** Linha do histórico; em Pix recebido ainda devolvível, mostra a ação "Devolver" (a tela confere de novo no servidor). */
@Composable
fun PixEntryRow(entry: PixEntry, onRefund: ((PixEntry) -> Unit)? = null) {
    Column {
        PixEntryLine(entry)
        if (onRefund != null && entry.canRefund) {
            TextButton(onClick = { onRefund(entry) }, modifier = Modifier.padding(start = 52.dp)) {
                Text("Devolver (até ${Money.format(entry.refundableAmount!!.amount)})")
            }
        }
    }
}

/** Um Pix do histórico: enviado em cinza com "−", recebido em verde com "+"; a contraparte vem mascarada do servidor. */
@Composable
private fun PixEntryLine(entry: PixEntry) {
    val received = !entry.sent
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (received) creditColor().copy(alpha = 0.14f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (received) "+" else "−",
                        color = if (received) creditColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entryTitle(entry),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    listOfNotNull(Format.dateTime(entry.createdAt), entry.message).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MoneyText(
            "${if (received) "+" else "−"} ${Money.format(entry.amount.amount)}",
            style = MaterialTheme.typography.titleSmall,
            color = if (received) creditColor() else Color.Unspecified,
        )
    }
}

/** QR code desenhado direto no Canvas: sempre preto sobre branco (leitores precisam do contraste, mesmo no tema escuro). */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrCode.matrix(text) }
    Canvas(
        modifier = modifier.fillMaxWidth().aspectRatio(1f).background(Color.White).padding(12.dp)
            .semantics { contentDescription = "QR code do Pix" },
    ) {
        val n = matrix.size
        val cell = size.minDimension / n
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix[y][x]) {
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}

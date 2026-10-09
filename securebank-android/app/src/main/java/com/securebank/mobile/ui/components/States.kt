package com.securebank.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.securebank.mobile.ui.theme.CardShape
import com.securebank.mobile.ui.theme.Sb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Esqueleto de carregamento (bloco neutro; sem animação para respeitar quem reduz movimento). */
@Composable
fun Skeleton(height: Dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.medium),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        content = {},
    )
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text("Tentar novamente") }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

/** Cartão de vidro: translúcido sobre o degradê do fundo, borda fina (listas usam divisórias, sem cartão dentro de cartão). */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = Sb.colors.glass,
        border = BorderStroke(1.dp, Sb.colors.glassBorder),
        content = content,
    )
}

/** Cartão de destaque (saldo): o brilho do acento num canto, sem gradiente chamativo. */
@Composable
fun HeroPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = Sb.colors.glass,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Box(
            Modifier.drawBehind {
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.9f))
            },
        ) { content() }
    }
}

@Composable
fun Badge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Valor em reais com algarismos de largura fixa, para os saldos alinharem. */
@Composable
fun MoneyText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Text(text, modifier = modifier, style = style.copy(fontFeatureSettings = "tnum"), color = color)
}

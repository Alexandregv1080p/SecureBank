package com.securebank.mobile.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.network.CardDetails
import com.securebank.mobile.core.util.CardValidation

private val CardGradient = Brush.linearGradient(listOf(Color(0xFF5B45E0), Color(0xFF7A5CF0), Color(0xFF2B2170)))

/**
 * O cartão desenhado, igual nos dois temas (como o plástico). Sem os dados completos mostra só os 4 últimos dígitos;
 * bloqueado ou cancelado fica apagado e com a situação escrita (a cor sozinha não conta).
 */
@Composable
fun CardView(card: Card, details: CardDetails? = null, modifier: Modifier = Modifier) {
    val off = card.status != "ACTIVE"
    val number = details?.let { CardValidation.formatNumber(it.number) } ?: "•••• •••• •••• ${card.last4}"
    val description = "Cartão de débito final ${card.last4}, ${CardValidation.statusLabel(card.status).lowercase()}"
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .alpha(if (off) 0.6f else 1f)
            .clip(RoundedCornerShape(20.dp))
            .background(CardGradient)
            .semantics { contentDescription = description }
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("SecureBank", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (off) {
                        Text(
                            CardValidation.statusLabel(card.status),
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text("DÉBITO", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, letterSpacing = 1.sp)
                }
            }
            Box(
                Modifier.width(36.dp).height(28.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFFBBF24)))),
            )
            Text(number, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 19.sp, letterSpacing = 1.5.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("TITULAR", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(card.holderName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VALIDADE", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(CardValidation.formatExpiry(card.expiryMonth, card.expiryYear), color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (details != null) {
                    Box(Modifier.size(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("CVV", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, letterSpacing = 1.sp)
                        Text(details.cvv, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

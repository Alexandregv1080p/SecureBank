package com.securebank.mobile.ui.piggy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.theme.creditColor

/** Linha de porquinho: nome, quanto tem, e barra de progresso quando há meta. */
@Composable
fun PiggyRow(piggy: Piggy, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(piggy.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            MoneyText(Money.format(piggy.balance.amount), style = MaterialTheme.typography.titleSmall)
        }
        PiggyProgress(piggy)
    }
}

@Composable
fun PiggyProgress(piggy: Piggy) {
    val goal = piggy.goal
    val percent = piggy.progressPercent
    if (goal == null || percent == null) {
        Text("Sem meta definida", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = if (piggy.goalReached) creditColor() else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )
        Text(
            if (piggy.goalReached) "Meta de ${Money.format(goal.amount)} alcançada!" else "$percent% de ${Money.format(goal.amount)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (piggy.goalReached) creditColor() else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

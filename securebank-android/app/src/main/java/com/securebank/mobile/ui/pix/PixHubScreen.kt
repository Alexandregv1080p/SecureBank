package com.securebank.mobile.ui.pix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.ui.components.NavRow
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton

/** Área Pix: os quatro caminhos e os últimos movimentos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixHubScreen(
    history: PixHistoryViewModel,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onKeys: () -> Unit,
    onHistory: () -> Unit,
    onCharges: () -> Unit,
    onSchedules: () -> Unit,
) {
    val s = history.state.collectAsStateWithLifecycle().value

    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = history::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pix", style = MaterialTheme.typography.headlineSmall)
                    Text("Envie e receba dinheiro na hora, a qualquer momento.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Panel {
                    Column {
                        NavRow("Enviar", "Por chave ou Pix Copia e Cola.", onSend)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        NavRow("Receber", "Mostre seu QR code ou copie o código.", onReceive)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        NavRow("Cobrar", "Crie uma cobrança com QR code de valor fixo.", onCharges)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        NavRow("Agendados", "Pix marcados para uma data futura.", onSchedules)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        NavRow("Minhas chaves", "CPF, e-mail, celular ou chave aleatória.", onKeys)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        NavRow("Histórico", "Pix enviados e recebidos.", onHistory)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Últimos Pix", style = MaterialTheme.typography.titleMedium)
                    when {
                        s.loading -> Skeleton(96.dp)
                        s.items.isEmpty() -> Text("Você ainda não fez nem recebeu um Pix.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> {
                            Panel {
                                Column {
                                    s.items.take(3).forEachIndexed { i, e ->
                                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        PixEntryRow(e)
                                    }
                                }
                            }
                            if (s.items.size > 3 || s.hasMore) TextButton(onClick = onHistory) { Text("Ver todo o histórico") }
                        }
                    }
                }
            }
        }
    }
}

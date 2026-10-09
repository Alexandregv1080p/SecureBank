package com.securebank.mobile.ui.more

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securebank.mobile.BuildConfig
import com.securebank.mobile.ui.components.Panel

@Composable
fun MoreScreen(darkTheme: Boolean, onToggleTheme: (Boolean) -> Unit, onTransfer: () -> Unit, onPiggies: () -> Unit, onInvestments: () -> Unit, onFx: () -> Unit, onNotifications: () -> Unit, onSecurity: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Mais", style = MaterialTheme.typography.headlineSmall)
        Panel {
            Column {
                MoreRow("Transferir entre contas", "Para outra conta do SecureBank por agência e número.", onTransfer)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                MoreRow("Porquinhos", "Reservas com meta, separadas do saldo da conta.", onPiggies)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                MoreRow("Investimentos", "Renda fixa simulada com rendimento diário.", onInvestments)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                MoreRow("Câmbio", "Compre e venda dólar e euro (simulado).", onFx)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                MoreRow("Avisos", "Movimentações e acessos recentes na sua conta.", onNotifications)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                MoreRow("Segurança", "Verificação em duas etapas, senha, dispositivos e bloqueio do app.", onSecurity)
            }
        }
        Panel {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tema escuro", style = MaterialTheme.typography.titleSmall)
                    Text("Desligue para usar o tema claro.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = darkTheme, onCheckedChange = onToggleTheme)
            }
        }
        TextButton(onClick = onLogout) { Text("Sair da conta", color = MaterialTheme.colorScheme.error) }
        Text("SecureBank ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

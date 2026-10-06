package com.securebank.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.Money

/** Escolha de conta por linhas selecionáveis (poucas contas: mais fácil de tocar que um menu suspenso). */
@Composable
fun AccountPicker(label: String, accounts: List<Account>, selectedId: String?, onSelect: (String) -> Unit, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Panel {
            Column {
                accounts.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = a.id == selectedId, onClick = { onSelect(a.id) }, role = Role.RadioButton)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = a.id == selectedId, onClick = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${Format.accountTypeLabel(a.type)}, ${a.accountNumber}", style = MaterialTheme.typography.titleSmall)
                            Text("Ag. ${a.branch}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MoneyText(Money.format(a.balance.amount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

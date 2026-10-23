package com.securebank.mobile.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.securebank.mobile.core.network.Card
import com.securebank.mobile.core.util.CardValidation

/**
 * Confirmação de bloquear ou cancelar: explica o que acontece, pede o motivo (escolher uma opção e, em "Outro motivo",
 * contar com as próprias palavras) e só então envia. Cancelar traz o aviso de que é definitivo e de que o caminho é pedir
 * outro cartão. O mesmo texto e as mesmas regras do web (lib/card.ts).
 */
@Composable
fun CardActionDialog(card: Card, action: CardAction, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val cancel = action == CardAction.CANCEL
    val options = if (cancel) CardValidation.cancelReasons else CardValidation.blockReasons
    var choice by remember(action) { mutableStateOf<String?>(null) }
    var details by remember(action) { mutableStateOf("") }
    var touched by remember(action) { mutableStateOf(false) }
    val option = options.firstOrNull { it.id == choice }
    val issue = CardValidation.reasonIssue(option, details)
    val maxDetails = if (option != null && !option.other) CardValidation.REASON_MAX - option.label.length - 2 else CardValidation.REASON_MAX

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(if (cancel) Icons.Filled.Warning else Icons.Filled.Lock, contentDescription = null, tint = if (cancel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        title = { Text(if (cancel) "Cancelar este cartão?" else "Bloquear este cartão?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cancel) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Cancelar é definitivo", style = MaterialTheme.typography.titleSmall)
                            Text("O cartão final ${card.last4} deixa de funcionar na hora e não pode ser reativado.", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Para voltar a comprar, é só pedir um cartão novo: ele vem com outro número, validade e CVV. Assinaturas e compras recorrentes ligadas a este cartão precisam ser atualizadas.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    Text(
                        "Enquanto o cartão final ${card.last4} estiver bloqueado, nenhuma compra passa. Você desbloqueia quando quiser.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Por que você está ${if (cancel) "cancelando" else "bloqueando"}?", style = MaterialTheme.typography.titleSmall)
                options.forEach { o ->
                    val selected = o.id == choice
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !busy, role = Role.RadioButton) { choice = o.id },
                    ) {
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null, enabled = !busy, modifier = Modifier.padding(12.dp))
                            Text(o.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (touched && option == null) Text("Escolha o motivo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (option != null) {
                    com.securebank.mobile.ui.components.SbTextField(
                        label = if (option.other) "Conte o que aconteceu" else "Quer contar mais? (opcional)",
                        value = details,
                        onValueChange = { details = it.replace('\n', ' ').take(maxDetails) },
                        error = if (touched && option.other) issue else null,
                        hint = "${details.trim().length}/$maxDetails",
                        enabled = !busy,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    touched = true
                    if (issue == null && option != null) onConfirm(CardValidation.composeReason(option, details))
                },
                enabled = !busy,
                colors = if (cancel) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
            ) { Text(if (cancel) "Cancelar cartão" else "Bloquear cartão") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Voltar") } },
    )
}

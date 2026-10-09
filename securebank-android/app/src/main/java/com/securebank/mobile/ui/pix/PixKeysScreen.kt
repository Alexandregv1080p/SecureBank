package com.securebank.mobile.ui.pix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.PixKey
import com.securebank.mobile.core.util.PixValidation
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.Skeleton

@Composable
fun PixKeysScreen(viewModel: PixKeysViewModel) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    var removing by remember { mutableStateOf<PixKey?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Minhas chaves", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Com uma chave, qualquer pessoa te paga sem precisar dos dados da conta. Até 5 chaves.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        s.error?.let { Banner(it) }
        s.done?.let { Banner(it, isError = false) }

        when (val keys = s.keys) {
            Load.Loading -> Skeleton(96.dp)
            is Load.Failed -> ErrorState(keys.message, onRetry = viewModel::load)
            is Load.Ready -> {
                if (keys.value.isEmpty()) {
                    Text("Você ainda não tem chaves cadastradas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Panel {
                        Column {
                            keys.value.forEachIndexed { i, k ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(PixValidation.typeLabel(k.type), style = MaterialTheme.typography.titleSmall)
                                        Text(k.key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { removing = k }, enabled = !s.working) { Text("Remover", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }

                // CPF, e-mail e celular existem uma vez só por cliente; a chave aleatória pode ser criada de novo
                val used = keys.value.map { it.type }.toSet()
                val available = listOf("CPF", "EMAIL", "PHONE", "RANDOM").filter { it == "RANDOM" || it !in used }
                if (keys.value.size < 5 && available.isNotEmpty()) {
                    Text("Cadastrar chave", style = MaterialTheme.typography.titleMedium)
                    val accounts = (s.accounts as? Load.Ready)?.value.orEmpty()
                    if (accounts.size > 1) AccountPicker("Conta que recebe", accounts, s.accountId, viewModel::onAccount, null)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { type ->
                            OutlinedButton(onClick = { viewModel.register(type) }, enabled = !s.working && s.accountId != null, modifier = Modifier.fillMaxWidth()) {
                                Text(if (type == "RANDOM") "Gerar chave aleatória" else "Cadastrar ${PixValidation.typeLabel(type)}")
                            }
                        }
                    }
                    Text(
                        "CPF, e-mail e celular saem do seu cadastro: não dá para registrar a chave de outra pessoa.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    removing?.let { key ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remover esta chave?") },
            text = { Text("Quem tentar pagar para ${key.key} não vai mais te encontrar. Você pode cadastrá-la de novo depois.") },
            confirmButton = { TextButton(onClick = { removing = null; viewModel.delete(key.id) }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
}

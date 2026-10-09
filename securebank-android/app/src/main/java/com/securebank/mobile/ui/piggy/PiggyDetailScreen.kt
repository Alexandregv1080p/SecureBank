package com.securebank.mobile.ui.piggy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.Piggy
import com.securebank.mobile.core.util.Money
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.MoneyText
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton

@Composable
fun PiggyDetailScreen(viewModel: PiggyDetailViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(s.closed) { if (s.closed) onBack() }
    var confirmClose by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        when (val piggy = s.piggy) {
            Load.Loading -> Skeleton(160.dp)
            is Load.Failed -> ErrorState(piggy.message, onRetry = viewModel::load)
            is Load.Ready -> {
                Header(piggy.value)
                s.error?.let { Banner(it) }
                s.done?.let { Banner(it, isError = false) }

                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Guardar ou resgatar", style = MaterialTheme.typography.titleMedium)
                        SbTextField(
                            "Valor (R$)", s.amount, viewModel::onAmount, error = s.amountError, mask = com.securebank.mobile.core.util.Validation::sanitizeAmount,
                            keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, enabled = !s.loading,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { viewModel.submit(PiggyAction.SAVE) }, enabled = !s.loading, modifier = Modifier.weight(1f)) { Text("Guardar") }
                            OutlinedButton(onClick = { viewModel.submit(PiggyAction.REDEEM) }, enabled = !s.loading, modifier = Modifier.weight(1f)) { Text("Resgatar") }
                        }
                        Text(
                            "Guardar tira da conta; resgatar devolve para a conta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Panel {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Editar", style = MaterialTheme.typography.titleMedium)
                        s.editError?.let { Banner(it) }
                        SbTextField("Nome", s.editName, viewModel::onEditName, enabled = !s.loading)
                        SbTextField(
                            "Meta (R$)", s.editGoal, viewModel::onEditGoal, mask = com.securebank.mobile.core.util.Validation::sanitizeAmount, keyboardType = KeyboardType.Decimal,
                            hint = "Em branco = sem meta", enabled = !s.loading,
                        )
                        PrimaryButton("Salvar alterações", onClick = viewModel::saveEdits, loading = s.loading)
                    }
                }

                TextButton(onClick = { confirmClose = true }, enabled = !s.loading) {
                    Text("Fechar porquinho", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Fechar este porquinho?") },
            text = { Text("O que estiver nele volta para a sua conta. Você pode criar outro quando quiser.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; viewModel.close() }) { Text("Fechar") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Header(piggy: Piggy) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(piggy.name, style = MaterialTheme.typography.headlineSmall)
        MoneyText(Money.format(piggy.balance.amount), style = MaterialTheme.typography.displaySmall)
        PiggyProgress(piggy)
    }
}

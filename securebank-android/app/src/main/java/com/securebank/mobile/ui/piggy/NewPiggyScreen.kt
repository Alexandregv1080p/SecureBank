package com.securebank.mobile.ui.piggy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.network.Account
import com.securebank.mobile.core.util.PiggyField
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.AccountPicker
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.EmptyState
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton

@Composable
fun NewPiggyScreen(viewModel: NewPiggyViewModel, onCreated: (String) -> Unit, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(s.createdId) { s.createdId?.let(onCreated) }

    AuthScaffold("Novo porquinho", "Dê um nome e, se quiser, uma meta. Você guarda e resgata quando quiser.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            s.error?.let { Banner(it) }
            when (val accounts = s.accounts) {
                Load.Loading -> Skeleton(128.dp)
                is Load.Failed -> ErrorState(accounts.message, onRetry = viewModel::loadAccounts)
                is Load.Ready -> if (accounts.value.isEmpty()) {
                    EmptyState("Você precisa de uma conta", "Abra uma conta antes de criar um porquinho.")
                } else {
                    Form(s, accounts.value, viewModel)
                }
            }
            TextButton(onClick = onBack) { Text("Cancelar") }
        }
    }
}

@Composable
private fun Form(s: NewPiggyState, accounts: List<Account>, vm: NewPiggyViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SbTextField("Nome", s.name, vm::onName, error = s.errors[PiggyField.Name], hint = "Ex.: Viagem, Reserva de emergência", enabled = !s.loading)
        SbTextField(
            "Meta (opcional)", s.goal, vm::onGoal, error = s.errors[PiggyField.Goal], mask = com.securebank.mobile.core.util.Validation::sanitizeAmount, keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done, onDone = vm::create, hint = "Deixe em branco para guardar sem meta", enabled = !s.loading,
        )
        if (accounts.size > 1) AccountPicker("Guardar a partir da conta", accounts, s.accountId, vm::onAccount, s.errors[PiggyField.Account])
        PrimaryButton("Criar porquinho", onClick = vm::create, loading = s.loading)
    }
}

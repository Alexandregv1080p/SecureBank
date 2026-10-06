package com.securebank.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField

@Composable
fun LoginScreen(viewModel: LoginViewModel, sessionExpired: Boolean, onRegister: () -> Unit) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    if (state.mfaToken != null) {
        AuthScaffold("Verificação em duas etapas", "Digite o código de 6 dígitos do seu aplicativo autenticador.") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.error?.let { Banner(it) }
                SbTextField(
                    label = "Código",
                    value = state.code,
                    onValueChange = viewModel::onCode, // envia sozinho ao completar 6 dígitos
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    onDone = viewModel::submitCode,
                    enabled = !state.loading,
                )
                PrimaryButton("Confirmar", onClick = viewModel::submitCode, loading = state.loading)
                TextButton(onClick = viewModel::cancelMfa) { Text("Voltar") }
            }
        }
        return
    }

    AuthScaffold("Entrar", "Acesse sua conta SecureBank.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sessionExpired) Banner("Sua sessão expirou. Entre novamente.", isError = false)
            state.error?.let { Banner(it) }
            SbTextField(
                label = "E-mail",
                value = state.email,
                onValueChange = viewModel::onEmail,
                error = state.emailError,
                keyboardType = KeyboardType.Email,
                enabled = !state.loading,
            )
            SbTextField(
                label = "Senha",
                value = state.password,
                onValueChange = viewModel::onPassword,
                error = state.passwordError,
                password = true,
                imeAction = ImeAction.Done,
                onDone = viewModel::submit,
                enabled = !state.loading,
            )
            PrimaryButton("Entrar", onClick = viewModel::submit, loading = state.loading)
            TextButton(onClick = onRegister, modifier = Modifier) { Text("Criar conta") }
        }
    }
}

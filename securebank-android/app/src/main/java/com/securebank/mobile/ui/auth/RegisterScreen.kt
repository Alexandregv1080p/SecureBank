package com.securebank.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import com.securebank.mobile.core.util.Validation
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.core.util.RegisterField
import com.securebank.mobile.ui.components.AuthScaffold
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField

@Composable
fun RegisterScreen(viewModel: RegisterViewModel, onBack: () -> Unit) {
    val s = viewModel.state.collectAsStateWithLifecycle().value

    AuthScaffold("Abra sua conta", "Cadastro gratuito. Depois você pode ativar a verificação em duas etapas.") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            s.error?.let { Banner(it) }
            SbTextField("Nome completo", s.name, { viewModel.onChange(RegisterField.Name, it) },
                error = s.errors[RegisterField.Name], enabled = !s.loading)
            SbTextField("CPF", s.document, { viewModel.onChange(RegisterField.Document, it) },
                error = s.errors[RegisterField.Document], keyboardType = KeyboardType.Number, hint = "000.000.000-00", mask = Validation::maskCpf, enabled = !s.loading)
            SbTextField("Celular", s.phone, { viewModel.onChange(RegisterField.Phone, it) },
                error = s.errors[RegisterField.Phone], keyboardType = KeyboardType.Phone, hint = "Com DDD, ex.: (11) 99999-8888", mask = Validation::maskPhone, enabled = !s.loading)
            SbTextField("E-mail", s.email, { viewModel.onChange(RegisterField.Email, it) },
                error = s.errors[RegisterField.Email], keyboardType = KeyboardType.Email, enabled = !s.loading)
            SbTextField("Senha", s.password, { viewModel.onChange(RegisterField.Password, it) },
                error = s.errors[RegisterField.Password], password = true,
                hint = "12 a 128 caracteres, sem ser previsível e sem conter o seu e-mail. Uma frase longa vale mais que símbolos.", enabled = !s.loading)
            SbTextField("Confirmar senha", s.confirm, { viewModel.onChange(RegisterField.Confirm, it) },
                error = s.errors[RegisterField.Confirm], password = true, imeAction = ImeAction.Done,
                onDone = viewModel::submit, enabled = !s.loading)
            PrimaryButton("Criar conta", onClick = viewModel::submit, loading = s.loading)
            TextButton(onClick = onBack) { Text("Já tenho cadastro") }
        }
    }
}

package com.securebank.mobile.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.securebank.mobile.AppContainer
import com.securebank.mobile.ui.components.CenteredColumn
import com.securebank.mobile.ui.components.PrimaryButton
import kotlinx.coroutines.launch

/** Pede a biometria (ou o PIN do aparelho) ao abrir o app e ao voltar de segundo plano. Nada do app aparece antes. */
@Composable
fun LockScreen(container: AppContainer) {
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()

    fun ask() = container.biometric.prompt(
        activity = activity,
        title = "Desbloquear o SecureBank",
        subtitle = "Confirme que é você para ver suas contas.",
        onSuccess = container.appLock::unlock,
        onFailure = {}, // fica na tela, com o botão para tentar de novo
    )

    LaunchedEffect(Unit) { ask() }

    CenteredColumn {
        Text("App bloqueado", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use a biometria ou o desbloqueio do aparelho.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Desbloquear", onClick = { ask() })
        TextButton(onClick = { scope.launch { container.auth.logout() } }) { Text("Sair da conta") }
    }
}

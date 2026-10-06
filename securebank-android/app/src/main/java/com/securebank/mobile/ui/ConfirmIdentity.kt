package com.securebank.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalActivity
import androidx.fragment.app.FragmentActivity
import com.securebank.mobile.AppContainer

/**
 * Operações que tiram dinheiro da conta (saque, transferência, pagamento) pedem a biometria (ou o PIN do aparelho) NA
 * HORA de confirmar, mesmo com o app já desbloqueado: quem pegou o celular destravado não consegue mover dinheiro.
 * Sem bloqueio ativo (desligado ou aparelho sem tela de bloqueio) a ação segue direto.
 *
 * Uso: `val confirm = rememberIdentityConfirmation(container, "Confirme a transferência"); confirm { viewModel.confirm() }`
 */
@Composable
fun rememberIdentityConfirmation(container: AppContainer, reason: String): (() -> Unit) -> Unit {
    val activity = LocalActivity.current as FragmentActivity
    return remember(container, activity, reason) {
        { action ->
            if (container.appLock.isEnabled()) {
                container.biometric.prompt(
                    activity = activity,
                    title = "Confirmar operação",
                    subtitle = reason,
                    onSuccess = action,
                    onFailure = {}, // cancelou ou errou: nada é enviado
                )
            } else {
                action()
            }
        }
    }
}

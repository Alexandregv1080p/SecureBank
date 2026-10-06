package com.securebank.mobile.ui.more

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.network.SessionInfo
import com.securebank.mobile.core.util.Format
import com.securebank.mobile.core.util.PasswordField
import com.securebank.mobile.ui.Load
import com.securebank.mobile.ui.components.Badge
import com.securebank.mobile.ui.components.Banner
import com.securebank.mobile.ui.components.ErrorState
import com.securebank.mobile.ui.components.Panel
import com.securebank.mobile.ui.components.PrimaryButton
import com.securebank.mobile.ui.components.SbTextField
import com.securebank.mobile.ui.components.Skeleton
import com.securebank.mobile.ui.rememberIdentityConfirmation

@Composable
fun SecurityScreen(container: AppContainer, onBack: () -> Unit) {
    val mfa: MfaViewModel = viewModel(factory = viewModelFactory { initializer { MfaViewModel(container.security) } })
    val password: PasswordViewModel = viewModel(factory = viewModelFactory { initializer { PasswordViewModel(container.security) } })
    val sessions: SessionsViewModel = viewModel(factory = viewModelFactory { initializer { SessionsViewModel(container.security) } })

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Mais") }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Segurança", style = MaterialTheme.typography.headlineSmall)
            Text("Controle como você acessa a sua conta.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LockPanel(container)
        MfaPanel(mfa)
        PasswordPanel(password)
        SessionsPanel(sessions)
    }
}

@Composable
private fun SectionTitle(text: String, badge: @Composable (() -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        badge?.invoke()
    }
}

// ---------------------------------------------------------------- bloqueio do app
@Composable
private fun LockPanel(container: AppContainer) {
    val available = remember { container.biometric.isAvailable() }
    var enabled by remember { mutableStateOf(container.lockSettings.enabled) }
    val confirm = rememberIdentityConfirmation(container, "Confirme para desligar o bloqueio")

    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Bloqueio do app")
            if (!available) {
                Text(
                    "Defina uma tela de bloqueio ou cadastre uma biometria no aparelho para proteger o app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pedir biometria ou o desbloqueio do aparelho ao abrir o app e ao voltar depois de um tempo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(checked = enabled, onCheckedChange = { wanted ->
                        if (wanted) {
                            container.lockSettings.enabled = true
                            enabled = true
                        } else {
                            // desligar a proteção também exige a identidade: um ladrão não a desliga
                            confirm {
                                container.lockSettings.enabled = false
                                enabled = false
                            }
                        }
                    })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- MFA
@Composable
private fun MfaPanel(vm: MfaViewModel) {
    val s = vm.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val enabled = (s.enabled as? Load.Ready)?.value
            SectionTitle("Verificação em duas etapas") {
                if (enabled != null) Badge(if (enabled) "Ativa" else "Desativada")
            }
            when (val status = s.enabled) {
                Load.Loading -> Skeleton(64.dp)
                is Load.Failed -> ErrorState(status.message, onRetry = vm::load)
                is Load.Ready -> Unit
            }
            s.error?.let { Banner(it) }

            if (enabled == false && s.setup == null) {
                Text(
                    "Peça um código do seu aplicativo autenticador a cada login. Protege a conta mesmo que a senha vaze.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton("Ativar", onClick = vm::start, loading = s.loading)
            }

            if (s.setup != null) {
                Text(
                    "1. Abra o aplicativo autenticador com o botão abaixo (ou digite a chave nele). 2. Digite o código de 6 dígitos que ele mostrar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { openAuthenticator(context, s.setup.otpauthUri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir no aplicativo autenticador")
                }
                Text("Chave de configuração", style = MaterialTheme.typography.labelLarge)
                Text(s.setup.secret.chunked(4).joinToString(" "), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { copySensitive(context, s.setup.secret) }) { Text("Copiar chave") }
                CodeField(s, vm)
                PrimaryButton("Confirmar e ativar", onClick = vm::submit, loading = s.loading)
                TextButton(onClick = vm::cancel) { Text("Cancelar") }
            }

            if (enabled == true && s.setup == null) {
                Text(
                    "Para desativar, confirme com um código válido do aplicativo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CodeField(s, vm)
                PrimaryButton("Desativar", onClick = vm::submit, loading = s.loading)
            }
        }
    }
}

@Composable
private fun CodeField(s: MfaState, vm: MfaViewModel) {
    SbTextField(
        label = "Código",
        value = s.code,
        onValueChange = vm::onCode,
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
        onDone = vm::submit,
        enabled = !s.loading,
    )
}

/** Abre um autenticador que trate otpauth://; se não houver nenhum, a chave digitada continua valendo. */
private fun openAuthenticator(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Nenhum aplicativo autenticador encontrado. Digite a chave nele.", android.widget.Toast.LENGTH_LONG).show()
    }
}

/** Copia o segredo marcando-o como sensível (o Android 13+ não mostra a prévia nem o sincroniza). */
private fun copySensitive(context: Context, secret: String) {
    val clip = ClipData.newPlainText("Chave de verificação", secret)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

// ---------------------------------------------------------------- senha
@Composable
private fun PasswordPanel(vm: PasswordViewModel) {
    val s = vm.state.collectAsStateWithLifecycle().value

    Panel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Trocar senha")
            s.error?.let { Banner(it) }
            if (s.done) Banner("Senha alterada. Os outros dispositivos foram desconectados.", isError = false)
            SbTextField("Senha atual", s.current, { vm.onChange(PasswordField.Current, it) }, error = s.errors[PasswordField.Current], password = true, enabled = !s.loading)
            SbTextField("Nova senha", s.next, { vm.onChange(PasswordField.Next, it) }, error = s.errors[PasswordField.Next], password = true, hint = "Mínimo de 12 caracteres.", enabled = !s.loading)
            SbTextField(
                "Confirmar nova senha", s.confirm, { vm.onChange(PasswordField.Confirm, it) },
                error = s.errors[PasswordField.Confirm], password = true, imeAction = ImeAction.Done, onDone = vm::submit, enabled = !s.loading,
            )
            PrimaryButton("Alterar senha", onClick = vm::submit, loading = s.loading)
        }
    }
}

// ---------------------------------------------------------------- dispositivos
@Composable
private fun SessionsPanel(vm: SessionsViewModel) {
    val s = vm.state.collectAsStateWithLifecycle().value

    Panel {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Dispositivos conectados")
                s.error?.let { Banner(it) }
            }
            when (val sessions = s.sessions) {
                Load.Loading -> Skeleton(96.dp, Modifier.padding(16.dp))
                is Load.Failed -> ErrorState(sessions.message, onRetry = vm::load)
                is Load.Ready -> sessions.value.forEachIndexed { i, session ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = if (i == 0) 12.dp else 0.dp))
                    SessionRow(session, revoking = s.revokingId == session.id, onRevoke = { vm.revoke(session.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionInfo, revoking: Boolean, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Format.device(session.userAgent), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                if (session.current) Badge("Este dispositivo")
                if (session.mfaVerified) Badge("2 etapas")
            }
            Text(
                "IP ${session.ip ?: "desconhecido"}, iniciada em ${Format.dateTime(session.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!session.current) OutlinedButton(onClick = onRevoke, enabled = !revoking) { Text(if (revoking) "…" else "Encerrar") }
    }
}

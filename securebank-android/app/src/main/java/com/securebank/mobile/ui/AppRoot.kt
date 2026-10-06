package com.securebank.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.session.SessionState
import com.securebank.mobile.ui.auth.LockScreen
import com.securebank.mobile.ui.auth.LoginScreen
import com.securebank.mobile.ui.auth.LoginViewModel
import com.securebank.mobile.ui.auth.RegisterScreen
import com.securebank.mobile.ui.auth.RegisterViewModel
import com.securebank.mobile.ui.components.CenteredColumn
import com.securebank.mobile.ui.components.PrimaryButton
import kotlinx.coroutines.launch

/** Raiz da interface: decide a tela pelo estado da sessão e do bloqueio. */
@Composable
fun AppRoot(container: AppContainer) {
    val state = container.session.state.collectAsStateWithLifecycle().value
    val locked = container.appLock.locked.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) { container.restoreSession() }
    LaunchedEffect(state) {
        if (state is SessionState.SignedOut) container.appLock.reset() // sem sessão não há o que bloquear
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        when (state) {
            SessionState.Restoring -> CenteredColumn { CircularProgressIndicator() }
            is SessionState.SignedOut -> SignedOutNav(container, sessionExpired = state.expired)
            is SessionState.SignedIn -> if (locked) LockScreen(container) else HomePlaceholder(container)
        }
    }
}

@Composable
private fun SignedOutNav(container: AppContainer, sessionExpired: Boolean) {
    var registering by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = registering) { registering = false }

    if (registering) {
        val vm: RegisterViewModel = viewModel(factory = viewModelFactory { initializer { RegisterViewModel(container.auth) } })
        RegisterScreen(vm, onBack = { registering = false })
    } else {
        val vm: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(container.auth) } })
        LoginScreen(vm, sessionExpired, onRegister = { registering = true })
    }
}

/** Marcador até a A3 (contas e extrato): só confirma a sessão e permite sair. */
@Composable
private fun HomePlaceholder(container: AppContainer) {
    val scope = rememberCoroutineScope()
    CenteredColumn {
        Text("Você está conectado.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Contas e extrato chegam na fase A3.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Sair", onClick = { scope.launch { container.auth.logout() } })
    }
}

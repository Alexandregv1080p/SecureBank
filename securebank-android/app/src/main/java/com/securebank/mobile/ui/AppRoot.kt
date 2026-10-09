package com.securebank.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/** Raiz da interface: decide a tela pelo estado da sessão e do bloqueio. */
@Composable
fun AppRoot(container: AppContainer) {
    val state = container.session.state.collectAsStateWithLifecycle().value
    val locked = container.appLock.locked.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) { container.restoreSession() }
    LaunchedEffect(state) {
        if (state is SessionState.SignedOut) container.appLock.reset() // sem sessão não há o que bloquear
    }

    Surface(color = androidx.compose.ui.graphics.Color.Transparent, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground) { // o fundo (degradê) vem do tema
        when (state) {
            SessionState.Restoring -> CenteredColumn { CircularProgressIndicator() }
            is SessionState.SignedOut -> SignedOutNav(container, sessionExpired = state.expired)
            is SessionState.SignedIn -> if (locked) LockScreen(container) else MainShell(container)
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

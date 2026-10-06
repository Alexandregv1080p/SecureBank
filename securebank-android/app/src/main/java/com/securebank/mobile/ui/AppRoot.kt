package com.securebank.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.AppContainer
import com.securebank.mobile.core.session.SessionState

/**
 * Raiz da interface: decide a tela pelo estado da sessão. Nesta fase (A1) só há marcadores; o login chega na A2.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val state = container.session.state.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) { container.restoreSession() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                SessionState.Restoring -> CircularProgressIndicator()
                is SessionState.SignedOut -> Text("Entrar (tela de login: fase A2)", style = MaterialTheme.typography.titleMedium)
                is SessionState.SignedIn -> Text("Início (fase A3)", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

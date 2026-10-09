package com.securebank.mobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securebank.mobile.ui.AppRoot
import com.securebank.mobile.ui.theme.SecureBankTheme

/** FragmentActivity (e não só ComponentActivity) porque o BiometricPrompt exige. */
class MainActivity : FragmentActivity() {
    private val container get() = (application as SecureBankApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Release: sem captura de tela, gravação nem miniatura nos apps recentes (saldo e extrato ficam na tela).
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        setContent {
            val dark by container.theme.dark.collectAsStateWithLifecycle()
            SecureBankTheme(darkTheme = dark) {
                AppRoot(container)
            }
        }
    }

    override fun onStop() {
        container.appLock.onBackgrounded()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForegrounded()
    }
}

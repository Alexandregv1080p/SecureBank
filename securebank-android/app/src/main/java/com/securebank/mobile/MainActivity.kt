package com.securebank.mobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.securebank.mobile.ui.AppRoot
import com.securebank.mobile.ui.theme.SecureBankTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Release: sem captura de tela, gravação nem miniatura nos apps recentes (saldo e extrato ficam na tela).
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        val container = (application as SecureBankApp).container
        setContent {
            SecureBankTheme {
                AppRoot(container)
            }
        }
    }
}

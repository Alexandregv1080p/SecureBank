package com.securebank.mobile.core.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema escolhido pelo usuário: escuro é o padrão (como no web) e a escolha fica guardada no aparelho. */
class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _dark = MutableStateFlow(prefs.getBoolean(KEY, true))
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    fun setDark(value: Boolean) {
        prefs.edit().putBoolean(KEY, value).apply()
        _dark.value = value
    }

    private companion object {
        const val KEY = "theme_dark"
    }
}

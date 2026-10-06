package com.securebank.mobile.core.security

import android.content.Context

/** Preferência do usuário (não é segredo, então SharedPreferences comum). Ligado por padrão. */
class LockSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("app_lock", true)
        set(value) = prefs.edit().putBoolean("app_lock", value).apply()
}

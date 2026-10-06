package com.securebank.mobile.core.session

/** Guarda o refresh token (a única credencial de longa duração do app). */
interface SecureTokenStore {
    fun get(): String?
    fun set(token: String)
    fun clear()
}

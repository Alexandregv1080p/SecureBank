package com.securebank.mobile.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM com uma chave que NUNCA sai do Android Keystore (não é exportável: nem o app nem um backup a leem).
 * Só o texto cifrado (IV + dados + tag de autenticação) fica nas SharedPreferences. Se a chave sumir (troca de tela de
 * bloqueio, restauração em outro aparelho) ou o dado for adulterado, a leitura falha e o token é descartado: o usuário
 * simplesmente entra de novo.
 */
class KeystoreTokenStore(context: Context) : SecureTokenStore {
    private val prefs = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)

    override fun get(): String? {
        val stored = prefs.getString(ENTRY, null) ?: return null
        return try {
            val (iv, data) = stored.split(':').map { Base64.getDecoder().decode(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    override fun set(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        prefs.edit().putString(ENTRY, encoder.encodeToString(cipher.iv) + ":" + encoder.encodeToString(data)).apply()
    }

    override fun clear() {
        prefs.edit().remove(ENTRY).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "securebank_refresh_token"
        const val ENTRY = "refresh"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

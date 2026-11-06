package org.osmwatch.changesetwatch.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Token OAuth cifrato con una chiave AES-GCM dell'Android Keystore (non esce dal dispositivo); in
 * SharedPreferences resta solo il cifrato.
 */
class TokenStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    /** null se assente o non decifrabile (chiave persa: serve un nuovo login). */
    var token: String?
        get() =
            try {
                val raw = Base64.decode(sp.getString("token", null) ?: return null, Base64.NO_WRAP)
                val c =
                    Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_LEN))
                    }
                String(c.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        set(v) {
            if (v == null) {
                sp.edit().remove("token").apply()
                return
            }
            val c =
                Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val enc = c.iv + c.doFinal(v.toByteArray(Charsets.UTF_8))
            sp.edit().putString("token", Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
        }

    private companion object {
        const val ALIAS = "osmwatch_token"
        const val IV_LEN = 12
    }
}

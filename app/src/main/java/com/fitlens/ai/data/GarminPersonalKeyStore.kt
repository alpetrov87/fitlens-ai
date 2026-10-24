package com.fitlens.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the user's one-time Garmin Personal API key with an Android Keystore AES key. */
class GarminPersonalKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("garmin_personal_key", Context.MODE_PRIVATE)

    @Synchronized
    fun save(apiKey: String) {
        require(apiKey.length in 12..4096) { "Garmin Personal API key изглежда невалиден." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.iv + cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
        check(prefs.edit().putString(KEY_CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Garmin ключът не можа да се запази на устройството."
        }
    }

    @Synchronized
    fun load(): String? {
        val encoded = prefs.getString(KEY_CIPHER_TEXT, null) ?: return null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > GCM_IV_BYTES) { "Локалният Garmin ключ е повреден. Изтрий го и добави отново." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_BITS, combined.copyOfRange(0, GCM_IV_BYTES)))
        return String(cipher.doFinal(combined.copyOfRange(GCM_IV_BYTES, combined.size)), StandardCharsets.UTF_8)
    }

    @Synchronized
    fun clear() {
        check(prefs.edit().remove(KEY_CIPHER_TEXT).commit()) { "Garmin ключът не можа да бъде изтрит." }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    fun hasKey(): Boolean = prefs.contains(KEY_CIPHER_TEXT)

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fitlens_garmin_personal_key_v1"
        const val KEY_CIPHER_TEXT = "api_key_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}

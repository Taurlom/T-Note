package ru.taurlom.tnote.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пароль приложения Календарь (CalDAV) — только в зашифрованном виде:
 * ключ AES-256-GCM рождается внутри AndroidKeyStore и не покидает
 * устройство; в Preferences лежит лишь пара «вектор инициализации |
 * шифртекст» в Base64. Ключ доступа — логин пользователя (несколько
 * подписок одного аккаунта переиспользуют один пароль).
 */
@Singleton
class PasswordStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secretKey: SecretKey by lazy {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()
    }

    fun save(username: String, password: String) {
        prefs.edit().putString(storageKey(username), encrypt(password)).apply()
    }

    fun read(username: String): String? = prefs.getString(storageKey(username), null)?.let { decrypt(it) }

    fun clear(username: String) {
        prefs.edit().remove(storageKey(username)).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val (ivPart, textPart) = blob.split(SEPARATOR, limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(textPart, Base64.NO_WRAP))
            .toString(Charsets.UTF_8)
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun storageKey(username: String): String = "pwd:$username"

    private companion object {

        const val PREFS_NAME = "caldav-security"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tnote-caldav"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = "|"
    }
}

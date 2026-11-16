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
 * шифртекст» в Base64.
 *
 * Ключ доступа — «кому и от кого»: origin сервера + логин (несколько
 * подписок одного аккаунта на одном сервере переиспользуют один пароль).
 * Привязка к origin не дала бы сохранённому паролю уехать на чужой сервер,
 * даже если адрес сервера в базе подменили — пароль без пары
 * (origin, логин) просто не находится. Импорт резервной копии стирает
 * секреты целиком (BackupRepositoryImpl): копия приносит аккаунты без
 * паролей, и живые секреты не должны встретиться с чужими адресами.
 */
@Singleton
class PasswordStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val secretKey: SecretKey by lazy {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()
    }

    fun save(serverOrigin: String, username: String, password: String) {
        prefs.edit().putString(storageKey(serverOrigin, username), encrypt(password)).apply()
    }

    /**
     * Пароль для пары (origin сервера, логин); null — секретов нет.
     * Старая форма ключа (до привязки к серверу — «pwd:логин») подхватывается
     * один раз и перекладывается под новый ключ: подписки, подключённые
     * прежними версиями, продолжают работать, но только со своим сервером
     * (импорт копии, где подмена адреса и была опасна, к этому моменту уже
     * стёр все секреты).
     */
    fun read(serverOrigin: String, username: String): String? {
        prefs.getString(storageKey(serverOrigin, username), null)
            ?.let { stored -> return runCatching { decrypt(stored) }.getOrNull() }
        val legacy = prefs.getString(legacyKey(username), null) ?: return null
        val password = runCatching { decrypt(legacy) }.getOrNull() ?: return null
        save(serverOrigin, username, password)
        prefs.edit().remove(legacyKey(username)).apply()
        return password
    }

    fun clear(serverOrigin: String, username: String) {
        prefs.edit()
            .remove(storageKey(serverOrigin, username))
            // Старый ключ того же логина — тоже этот же секрет, иным
            // сервером он больше не должен находиться.
            .remove(legacyKey(username))
            .apply()
    }

    /** Все секреты прочь — восстановление резервной копии выключает подписки. */
    fun clearAll() {
        prefs.edit().clear().apply()
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

    /** Новый ключ: «кому (origin) и от кого (логин)». */
    private fun storageKey(serverOrigin: String, username: String): String = "pwd:$serverOrigin|$username"

    /** Ключ до привязки к серверу — только логин; подхватывается один раз (см. [read]). */
    private fun legacyKey(username: String): String = "pwd:$username"

    private companion object {

        const val PREFS_NAME = "caldav-security"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tnote-caldav"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = "|"
    }
}

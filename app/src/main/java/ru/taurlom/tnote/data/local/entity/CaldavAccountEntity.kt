package ru.taurlom.tnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Подключённый CalDAV-аккаунт (Яндекс первым, схема — из roadmap этапа 1):
 * один аккаунт — один подписанный календарь-коллекция. Пароль хранится не
 * здесь, а в [ru.taurlom.tnote.data.security.PasswordStore] под ключом
 * по имени пользователя: шифртекст не должен попадать в файл базы данных,
 * который уходит в резервные копии.
 */
@Entity(tableName = "caldav_accounts")
data class CaldavAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Корень сервера, например «https://caldav.yandex.ru». */
    val serverUrl: String,
    /** Логин (для Яндекса — почта), он же ключ пароля в PasswordStore. */
    val username: String,
    /** Полный URL календаря-коллекции (href из PROPFIND). */
    val collectionUrl: String,
    /** Отображаемое имя календаря с сервера (displayname). */
    val collectionName: String,
    /** Момент последней успешной синхронизации (epoch millis, 0 — никогда). */
    val lastSyncAt: Long = 0,
    /** Сообщение последней ошибки синхронизации (null — ошибок нет). */
    val lastError: String? = null,
)

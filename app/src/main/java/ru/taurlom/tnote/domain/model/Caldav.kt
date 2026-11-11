package ru.taurlom.tnote.domain.model

/**
 * Провайдер синхронизации: один активный аккаунт — один источник.
 * Яндекс едет CalDAV-транспортом, Google — Calendar API v3; движок синка
 * и конвертеры общие.
 */
enum class CaldavProvider {
    YANDEX,
    GOOGLE,
}

/**
 * Подключённый аккаунт синхронизации на экране настроек: один аккаунт —
 * одна подписка на календарь (коллекция CalDAV или календарь Google).
 */
data class CaldavAccount(
    val id: Long = 0,
    /** Провайдер: решает транспорт и форму подключения. */
    val provider: CaldavProvider = CaldavProvider.YANDEX,
    /** Корень сервера, например «https://caldav.yandex.ru». */
    val serverUrl: String,
    /**
     * Логин: для Яндекса — почта (ключ пароля в PasswordStore), для
     * Google — адрес аккаунта (ключ OAuth-токена).
     */
    val username: String,
    /**
     * Идентификатор подписанного календаря: для Яндекса — URL коллекции
     * (href из PROPFIND), для Google — calendarId.
     */
    val collectionUrl: String,
    /** Отображаемое имя календаря с сервера (displayname / summary). */
    val collectionName: String,
    /** Момент последней успешной синхронизации (epoch millis, 0 — никогда). */
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
)

/**
 * Календарь-коллекция, найденный на этапе подключения (PROPFIND у Яндекса,
 * calendarList у Google).
 */
data class CaldavCalendarRef(val name: String, val url: String) {
    companion object {

        /** Сервер по умолчанию: Яндекс (первый этап CalDAV-интеграции). */
        const val DEFAULT_SERVER = "https://caldav.yandex.ru"

        /** Корень Google Calendar API v3 — serverUrl аккаунта Google. */
        const val GOOGLE_SERVER = "https://www.googleapis.com/calendar/v3"
    }
}

/**
 * Итог одной синхронизации — числа для тоста/снекса экрана.
 */
data class CaldavSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    /** Доведено до сервера изменений: PUT'ы правок и DELETE'ы удалений. */
    val pushed: Int = 0,
    /** Строк, перезаписанных более свежей серверной версией (last-write-wins). */
    val overwritten: Int = 0,
) {
    /** Суммирование результатов по всем аккаунтам за один прогон. */
    operator fun plus(other: CaldavSyncResult): CaldavSyncResult = CaldavSyncResult(
        added = added + other.added,
        updated = updated + other.updated,
        removed = removed + other.removed,
        pushed = pushed + other.pushed,
        overwritten = overwritten + other.overwritten,
    )
}

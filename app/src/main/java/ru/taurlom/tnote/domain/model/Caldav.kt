package ru.taurlom.tnote.domain.model

/**
 * Подключённый CalDAV-аккаунт на экране настроек: один аккаунт — одна
 * подписка на календарь-коллекцию сервера. События из коллекции появляются
 * в сетке приложения только для чтения (этап 1), правятся они на сервере.
 */
data class CaldavAccount(
    val id: Long = 0,
    /** Корень сервера, например «https://caldav.yandex.ru». */
    val serverUrl: String,
    val username: String,
    val collectionUrl: String,
    val collectionName: String,
    /** Момент последней успешной синхронизации (epoch millis, 0 — никогда). */
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
)

/**
 * Календарь-коллекция, найденный поиском PROPFIND на этапе подключения.
 */
data class CaldavCalendarRef(val name: String, val url: String) {
    companion object {

        /** Сервер по умолчанию: Яндекс (первый этап CalDAV-интеграции). */
        const val DEFAULT_SERVER = "https://caldav.yandex.ru"
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

package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavSyncResult

/**
 * CalDAV-подписка (первый сервер — Яндекс). Этапы: 1 — только чтение
 * (подтягиваем события сервера в сетку), 2 — двусторонняя синхронизация,
 * 3 — OAuth для Google.
 *
 * Действия бросают [CaldavSyncException] — экран локализует подписи,
 * репозиторий сообщениями исключений не занимается.
 */
interface CaldavRepository {

    /** Живой список подключённых аккаунтов. */
    fun observeAccounts(): Flow<List<CaldavAccount>>

    /** Поиск календарей с событиями (VEVENT) на сервере. */
    suspend fun discover(server: String, username: String, password: String): List<CaldavCalendarRef>

    /**
     * Сохранить аккаунт, пароль и запустить первую синхронизацию (она же
     * проверяет выбранную коллекцию по-настоящему). Ошибка первой синхронизации
     * не отменяет подключение — она пишется в lastError и видна на карточке.
     */
    suspend fun connect(server: String, username: String, password: String, calendar: CaldavCalendarRef): Long

    /** Отключить аккаунт: события этого источника удаляются вместе с ним. */
    suspend fun disconnect(accountId: Long)

    /** Ручная синхронизация одного аккаунта (кнопка «Обновить»). */
    suspend fun sync(accountId: Long): CaldavSyncResult

    /** Сводка по всем аккаунтам: воркер фоновый прогон. */
    suspend fun syncAll(): CaldavSyncResult
}

/**
 * Отказ CalDAV-синхронизации: типизированный вид вместо стектрейсов —
 * текст показывается на карточке аккаунта и в форме подключения.
 */
sealed class CaldavSyncException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Сеть не доехала: нет интернета, таймаут, DNS. */
    class Network(cause: Throwable) :
        CaldavSyncException(
            cause.message ?: "Не удалось связаться с сервером",
            cause,
        )

    /** Сервер отверг логин или пароль (401/403). */
    class Auth(message: String = "Сервер отверг логин или пароль") : CaldavSyncException(message)

    /** Сервер отвечает, но протокол нарушен (HTTP != 2xx, битый XML/ICS). */
    class Server(message: String) : CaldavSyncException(message)
}

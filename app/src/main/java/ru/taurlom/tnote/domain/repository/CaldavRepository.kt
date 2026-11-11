package ru.taurlom.tnote.domain.repository

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavProvider
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

    /** Поиск календарей с событиями на сервере выбранного провайдера. */
    suspend fun discover(provider: CaldavProvider, server: String, username: String, password: String): List<CaldavCalendarRef>

    /**
     * Сохранить аккаунт, секрет и запустить первую синхронизацию (она же
     * проверяет выбранную коллекцию по-настоящему). Ошибка первой синхронизации
     * не отменяет подключение — она пишется в lastError и видна на карточке.
     */
    suspend fun connect(provider: CaldavProvider, server: String, username: String, password: String, calendar: CaldavCalendarRef): Long

    /** Отключить аккаунт: события этого источника удаляются вместе с ним. */
    suspend fun disconnect(accountId: Long)

    /** Ручная синхронизация одного аккаунта (кнопка «Обновить»). */
    suspend fun sync(accountId: Long): CaldavSyncResult

    /** Сводка по всем аккаунтам: воркер фоновый прогон. */
    suspend fun syncAll(): CaldavSyncResult

    /** Живое число событий без приписки — «старьё», которого нет на сервере. */
    fun observeLocalOnlyCount(): Flow<Int>

    /**
     * Приписать все неприписанные события аккаунту и поставить в очередь
     * отправки (кнопка «добавить старые события в календарь»). Возвращает
     * число поставленных в очередь.
     */
    suspend fun publishLocalOnly(accountId: Long): Int
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

/**
 * Согласие Google не выдано (первое подключение или отзыв доступа): в
 * [intent] — намерение системного диалога («Разрешить T-Note доступ к
 * календарю»), форма подключения запускает его и повторяет операцию;
 * прочие места (карточка аккаунта, тосты) видят обычный отказ авторизации.
 * Живёт рядом с [CaldavSyncException], потому что тот sealed.
 */
class CaldavConsentRequired(val intent: Intent?) : CaldavSyncException("Нужно разрешить доступ к календарю")

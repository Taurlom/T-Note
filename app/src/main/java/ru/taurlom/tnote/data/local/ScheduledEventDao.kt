package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity

/**
 * Сколько bind-параметров попадается в один чанк списочного запроса:
 * SQLite в Android до 11 версии включительно отказывается от подготовленных
 * statements более чем с ~999 переменными (SQLITE_MAX_VARIABLE_NUMBER),
 * а minSdk проекта — 24. Запас — вдвое, на случай будущих условий в том
 * же запросе.
 */
internal const val DELETE_BIND_CHUNK = 500

@Dao
interface ScheduledEventDao {

    /**
     * События, пересекающиеся с месяцем [monthStart, nextMonthStart):
     * много-дневное событие видно в каждом месяце своего интервала,
     * включая промежуточные — у «30 января → 2 марта» ни одна дата не
     * имеет февральского префикса, но февраль он занимает. Даты
     * «yyyy-MM-dd» сравнимы лексикографически; endDate — последний
     * день включительно (см. ScheduledEvent.windowOn), без него интервал
     * вырождается в eventDate.
     */
    @Query(
        "SELECT * FROM scheduled_events WHERE eventDate < :nextMonthStart " +
            "AND COALESCE(endDate, eventDate) >= :monthStart " +
            "ORDER BY position ASC, id ASC",
    )
    fun getByMonth(monthStart: String, nextMonthStart: String): Flow<List<ScheduledEventEntity>>

    @Query("SELECT * FROM scheduled_events WHERE type = :type ORDER BY eventDate ASC")
    fun getByType(type: String): Flow<List<ScheduledEventEntity>>

    /** События без приписки подписке — кандидаты «залить старые в календарь». */
    @Query("SELECT COUNT(*) FROM scheduled_events WHERE caldavAccountId IS NULL")
    fun observeLocalOnlyCount(): Flow<Int>

    /** Приписка всех неприписанных событий аккаунту и в очередь отправки. */
    @Query(
        "UPDATE scheduled_events SET caldavAccountId = :accountId, dirty = 1 " +
            "WHERE caldavAccountId IS NULL",
    )
    suspend fun attributeAll(accountId: Long): Int

    @Query("SELECT * FROM scheduled_events WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ScheduledEventEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM scheduled_events WHERE eventDate = :date")
    suspend fun getMaxPosition(date: String): Int

    @Query("DELETE FROM scheduled_events WHERE eventDate = :date")
    suspend fun deleteByDate(date: String)

    /** События строго до [cutoff] («yyyy-MM-dd»): случай утраты решает домен. */
    @Query("SELECT * FROM scheduled_events WHERE eventDate < :cutoff")
    suspend fun getByDateBefore(cutoff: String): List<ScheduledEventEntity>

    @Query("DELETE FROM scheduled_events")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ScheduledEventEntity): Long

    @Update
    suspend fun update(event: ScheduledEventEntity)

    @Delete
    suspend fun delete(event: ScheduledEventEntity)

    /** Вариант для пачки: «Удалить прошедшие» — [deletePast]. */
    @Delete
    suspend fun delete(events: List<ScheduledEventEntity>)

    /**
     * Событие подписки по серверному UID — ключ применения diff'а.
     * Идентичность — пара (аккаунт, UID): строка другой подписки с тем же
     * UID (копия события, перенесённая между календарями, живёт под ним на
     * обоих серверах) здесь чужая и находке не помеха. Неприписанные
     * строки — кандидаты: «отключить и оставить события» стирает приписку,
     * а повторная вычитка того же календаря обязана присвоить строку
     * обратно по UID, а не завести дубликат; при выборе между своей и
     * неприписанной решает своя.
     */
    @Query(
        "SELECT * FROM scheduled_events WHERE uid = :uid " +
            "AND (caldavAccountId = :accountId OR caldavAccountId IS NULL) " +
            "ORDER BY caldavAccountId IS NULL LIMIT 1",
    )
    suspend fun getByUid(accountId: Long, uid: String): ScheduledEventEntity?

    /** Импортированные этим аккаунтом события — кандидаты удаления по
     *  разнице вычитки (dirty исключены: их правка ещё не уехала, см.
     *  [deleteNotInUids]). */
    @Query("SELECT * FROM scheduled_events WHERE caldavAccountId = :accountId AND dirty = 0")
    suspend fun getCleanFor(accountId: Long): List<ScheduledEventEntity>

    /**
     * Удаление по заранее вычисленному набору id — безопасная половина
     * [deleteNotInUids]: чанки фиксированного множества не могут съесть
     * друг друга, а повторная проверка приписки и очереди в SQL бережёт
     * строку, сменившую состояние между выбором и удалением.
     */
    @Query(
        "DELETE FROM scheduled_events WHERE id IN (:ids) " +
            "AND caldavAccountId = :accountId AND dirty = 0",
    )
    suspend fun deleteCleanByIds(ids: List<Long>, accountId: Long): Int

    /**
     * Импортированные этим аккаунтом события больше не на сервере.
     * dirty-строки исключены: локальная правка репетирует PUT и
     * воскресит объект сама — здесь её удалять нельзя.
     *
     * UID снапшота в запрос НЕ передаётся: Google отдаёт до 2500 объектов
     * страницей, а SQLite Android до 11 отказывается от bind'ов больше
     * ~999 — на большом календаре «uid NOT IN (:uids)» ронял бы разницу
     * каждого прогона (баг 19). Разница считается в памяти (кандидаты —
     * [getCleanFor]), удаление идёт чанками заранее вычисленных id —
     * так, что первый чанк не может удалить объекты остальных, чем
     * кончилось бы прямое чанкование «NOT IN».
     */
    @Transaction
    suspend fun deleteNotInUids(accountId: Long, uids: List<String>): Int {
        val keep = uids.toSet()
        val doomed = getCleanFor(accountId).filterNot { it.uid in keep }.map { it.id }
        var removed = 0
        doomed.chunked(DELETE_BIND_CHUNK).forEach { removed += deleteCleanByIds(it, accountId) }
        return removed
    }

    /** Все события источника — при отключении аккаунта. */
    @Query("DELETE FROM scheduled_events WHERE caldavAccountId = :accountId")
    suspend fun deleteByCaldavAccount(accountId: Long): Int

    /**
     * Отвязка всех событий источника — при отключении аккаунта с
     * сохранением: события остаются в приложении как локальные. Стираются
     * приписка, серверная привязка (href, ETag) и очередь отправки; UID
     * переживает — при повторном подключении того же календаря вычитка
     * сопоставит строки по UID, дубли не появятся.
     */
    @Query(
        "UPDATE scheduled_events SET caldavAccountId = NULL, serverHref = NULL, " +
            "etag = NULL, dirty = 0 WHERE caldavAccountId = :accountId",
    )
    suspend fun detachFromAccount(accountId: Long): Int

    /** События дня (одноразово) — перед удалением дня: собирать tombstones. */
    @Query("SELECT * FROM scheduled_events WHERE eventDate = :date")
    suspend fun getByDateOnce(date: String): List<ScheduledEventEntity>

    /** Все события с привязкой к CalDAV — tombstones при полной чистке. */
    @Query("SELECT * FROM scheduled_events WHERE caldavAccountId IS NOT NULL")
    suspend fun getAttributed(): List<ScheduledEventEntity>

    /** Ждут PUT: события, приписанные этому аккаунту (атрибуция — у репозитория). */
    @Query(
        "SELECT * FROM scheduled_events WHERE dirty = 1 " +
            "AND caldavAccountId = :accountId " +
            "ORDER BY id ASC",
    )
    suspend fun getDirtyFor(accountId: Long): List<ScheduledEventEntity>

    /**
     * PUT доехал: очередь чиста, ETag и href обновлены. CAS по
     * [sentRevision] — версией строки, которую отправляли: dirty снимается
     * лишь если за время сетевого хода строку локально не трогали. Возвращает
     * число изменённых строк (0 — правка уже вписана, её отправим заново).
     */
    @Query(
        "UPDATE scheduled_events SET dirty = 0, etag = :etag, serverHref = :href " +
            "WHERE id = :id AND localRevision = :sentRevision",
    )
    suspend fun markPushed(id: Long, etag: String?, href: String, sentRevision: Long): Int

    /**
     * Серверный ответ есть, а строка тем временем правится: ETag и href
     * запоминаем (следующий PUT пойдёт с живого предусловия, а не как первая
     * публикация), очередь не трогаем. Число изменённых строк: 0 — строки
     * уже нет (её удалили в сетевой ход, и дальше работает очередь DELETE).
     */
    @Query("UPDATE scheduled_events SET etag = :etag, serverHref = :href WHERE id = :id")
    suspend fun rememberServerState(id: Long, etag: String?, href: String): Int

    /** Бэкфилл маркера: строка встаёт в очередь отправки без правки данных. */
    @Query("UPDATE scheduled_events SET dirty = 1 WHERE id = :id")
    suspend fun markDirty(id: Long)
}

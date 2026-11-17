package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.data.local.entity.CalendarNoteEntity

@Dao
interface CalendarNoteDao {

    @Query(
        "SELECT * FROM calendar_notes WHERE eventDate LIKE :monthPrefix " +
            "ORDER BY eventDate ASC",
    )
    fun getByMonthPrefix(monthPrefix: String): Flow<List<CalendarNoteEntity>>

    @Query("DELETE FROM calendar_notes WHERE eventDate = :date")
    suspend fun deleteByDate(date: String)

    /** Одна заметка дня или null: правка должна помнить момент создания. */
    @Query("SELECT * FROM calendar_notes WHERE eventDate = :date")
    suspend fun getByDateOnce(date: String): CalendarNoteEntity?

    /** Отрывные заметки строго до [cutoff] («yyyy-MM-dd»); возвращает число. */
    @Query("DELETE FROM calendar_notes WHERE eventDate < :cutoff")
    suspend fun deleteBefore(cutoff: String): Int

    @Query("DELETE FROM calendar_notes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: CalendarNoteEntity): Long

    /** Приписанные подписке заметки — tombstones при полной чистке календаря. */
    @Query("SELECT * FROM calendar_notes WHERE caldavAccountId IS NOT NULL")
    suspend fun getAttributed(): List<CalendarNoteEntity>

    /** Приписанные заметки до [cutoff] («yyyy-MM-dd») — tombstones чистки прошлого. */
    @Query("SELECT * FROM calendar_notes WHERE caldavAccountId IS NOT NULL AND eventDate < :cutoff")
    suspend fun getAttributedBefore(cutoff: String): List<CalendarNoteEntity>

    /** Ждут PUT: заметки, приписанные этому аккаунту (приписка — у репозитория). */
    @Query(
        "SELECT * FROM calendar_notes WHERE dirty = 1 " +
            "AND caldavAccountId = :accountId " +
            "ORDER BY eventDate ASC",
    )
    suspend fun getDirtyFor(accountId: Long): List<CalendarNoteEntity>

    /**
     * PUT доехал: очередь чиста, ETag и href обновлены. CAS по
     * [sentRevision] (см. CalendarNoteEntity.localRevision): правка,
     * вписанная в сетевой ход, остаётся в очереди. Возвращает число
     * изменённых строк.
     */
    @Query(
        "UPDATE calendar_notes SET dirty = 0, etag = :etag, serverHref = :href " +
            "WHERE eventDate = :date AND localRevision = :sentRevision",
    )
    suspend fun markPushed(date: String, etag: String?, href: String, sentRevision: Long): Int

    /**
     * Ответ сервера есть, заметка тем временем правится: привязку пишем,
     * очередь не трогаем. [remoteUid] — фактический UID из вычитки; null
     * (конфликтный путь PUT'а) — предположений нет, сохраняется прежний,
     * следующая полная вычитка освежит.
     */
    @Query(
        "UPDATE calendar_notes SET etag = :etag, serverHref = :href, " +
            "remoteUid = COALESCE(:remoteUid, remoteUid) WHERE eventDate = :date",
    )
    suspend fun rememberServerState(date: String, etag: String?, href: String, remoteUid: String?): Int

    /**
     * Приписанные подписке чистые заметки — кандидаты удаления по разнице
     * вычитки (см. [deleteNotInDates]).
     */
    @Query("SELECT * FROM calendar_notes WHERE caldavAccountId = :accountId AND dirty = 0")
    suspend fun getCleanFor(accountId: Long): List<CalendarNoteEntity>

    /**
     * Удаление по заранее вычисленному набору дней — безопасная половина
     * [deleteNotInDates]: чанки фиксированного множества не могут съесть
     * друг друга, а повторная проверка приписки и очереди в SQL бережёт
     * строку, сменившую состояние между выбором и удалением.
     */
    @Query(
        "DELETE FROM calendar_notes WHERE eventDate IN (:dates) " +
            "AND caldavAccountId = :accountId AND dirty = 0",
    )
    suspend fun deleteCleanByDates(dates: List<String>, accountId: Long): Int

    /**
     * Заметка пропала на сервере, локальных правок нет (dirty = 0) —
     * вычитка знает лучший состав коллекции.
     *
     * Даты снапшота в запрос НЕ передаются: набор дней на многолетнем
     * календаре превышает ~999 bind-переменных SQLite Android до 11, и
     * «eventDate NOT IN (:dates)» ронял бы разницу каждого прогона
     * (баг 19). Разница считается в памяти, удаление идёт чанками
     * заранее вычисленных дней (см. ScheduledEventDao.deleteNotInUids).
     */
    @Transaction
    suspend fun deleteNotInDates(accountId: Long, dates: List<String>): Int {
        val keep = dates.toSet()
        val doomed = getCleanFor(accountId).filterNot { it.eventDate in keep }.map { it.eventDate }
        var removed = 0
        doomed.chunked(DELETE_BIND_CHUNK).forEach { removed += deleteCleanByDates(it, accountId) }
        return removed
    }

    /** Подключение подписки: локальные заметки приписываются ей и едут первым пушем. */
    @Query(
        "UPDATE calendar_notes SET caldavAccountId = :accountId, dirty = 1 " +
            "WHERE caldavAccountId IS NULL",
    )
    suspend fun attributeAll(accountId: Long): Int

    /**
     * Отвязка заметок источника (выключение подписки): строки остаются в
     * приложении локальными, серверная привязка (href, ETag, фактический
     * UID носителя) и очередь отправки стираются — по чужому адресу от
     * имени выключенной подписки писать уже некому, а uid другого
     * сервера следующим подключением не переиспользуется.
     */
    @Query(
        "UPDATE calendar_notes SET caldavAccountId = NULL, serverHref = NULL, " +
            "etag = NULL, dirty = 0, remoteUid = NULL WHERE caldavAccountId = :accountId",
    )
    suspend fun detachFromAccount(accountId: Long): Int
}

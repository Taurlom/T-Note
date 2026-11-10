package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    /** PUT доехал: очередь чиста, ETag и href обновлены. */
    @Query(
        "UPDATE calendar_notes SET dirty = 0, etag = :etag, serverHref = :href WHERE eventDate = :date",
    )
    suspend fun markPushed(date: String, etag: String?, href: String)

    /**
     * Заметка пропала на сервере, локальных правок нет (dirty = 0) —
     * вычитка знает лучший состав коллекции.
     */
    @Query(
        "DELETE FROM calendar_notes WHERE caldavAccountId = :accountId " +
            "AND dirty = 0 AND eventDate NOT IN (:dates)",
    )
    suspend fun deleteNotInDates(accountId: Long, dates: List<String>): Int

    /** Подключение подписки: локальные заметки приписываются ей и едут первым пушем. */
    @Query(
        "UPDATE calendar_notes SET caldavAccountId = :accountId, dirty = 1 " +
            "WHERE caldavAccountId IS NULL",
    )
    suspend fun attributeAll(accountId: Long): Int
}

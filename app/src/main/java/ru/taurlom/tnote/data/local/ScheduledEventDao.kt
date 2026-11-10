package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity

@Dao
interface ScheduledEventDao {

    @Query(
        "SELECT * FROM scheduled_events WHERE eventDate LIKE :monthPrefix " +
            "ORDER BY position ASC, id ASC",
    )
    fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEventEntity>>

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

    /** Единственное событие по серверному UID — ключ применения diff'а. */
    @Query("SELECT * FROM scheduled_events WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): ScheduledEventEntity?

    /** Импортированные этим аккаунтом события больше не на сервере.
     *  dirty-строки исключены: локальная правка репетирует PUT и
     *  воскресит объект сама — здесь её удалять нельзя. */
    @Query(
        "DELETE FROM scheduled_events WHERE caldavAccountId = :accountId " +
            "AND dirty = 0 AND uid NOT IN (:uids)",
    )
    suspend fun deleteNotInUids(accountId: Long, uids: List<String>): Int

    /** Все события источника — при отключении аккаунта. */
    @Query("DELETE FROM scheduled_events WHERE caldavAccountId = :accountId")
    suspend fun deleteByCaldavAccount(accountId: Long): Int

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

    /** PUT доехал: очередь чиста, ETag и href обновлены. */
    @Query(
        "UPDATE scheduled_events SET dirty = 0, etag = :etag, serverHref = :href WHERE id = :id",
    )
    suspend fun markPushed(id: Long, etag: String?, href: String)
}

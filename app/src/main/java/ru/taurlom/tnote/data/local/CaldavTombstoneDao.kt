package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.taurlom.tnote.data.local.entity.CaldavTombstoneEntity

/**
 * Очередь серверных удалений (этап 2): записи живут от локального
 * удаления до подтверждения сервером, sync между ними удаление не теряет.
 */
@Dao
interface CaldavTombstoneDao {

    /** Отложенные удаления аккаунта — push-шаг движка. */
    @Query("SELECT * FROM caldav_tombstones WHERE accountId = :accountId ORDER BY deletedAt ASC")
    suspend fun getAllFor(accountId: Long): List<CaldavTombstoneEntity>

    /** Живой tombstone события: pull такого UID обратно не вписывает. */
    @Query("SELECT * FROM caldav_tombstones WHERE uid = :uid")
    suspend fun getByUid(uid: String): CaldavTombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: CaldavTombstoneEntity)

    /** Сервер подтвердил DELETE (204) или «и так нет» (404). */
    @Query("DELETE FROM caldav_tombstones WHERE uid = :uid")
    suspend fun deleteByUid(uid: String): Int

    /**
     * Снятие удаления одной идентичности: локальное пересоздание (upsert)
     * всегда новее tombstone, а DELETE снёс бы свежий серверный объект.
     * Ограничение по аккаунту обязательно: у заметок UID выводится из даты,
     * поэтому очередь удаления другой подписки под тем же UID — это другое
     * намерение, и снимать его нельзя. Возвращает число снятых записей.
     */
    @Query("DELETE FROM caldav_tombstones WHERE uid = :uid AND accountId = :accountId")
    suspend fun deleteForIdentity(uid: String, accountId: Long): Int

    /** Отключение аккаунта: его очередь DELETE гасится целиком. */
    @Query("DELETE FROM caldav_tombstones WHERE accountId = :accountId")
    suspend fun deleteAllFor(accountId: Long): Int
}

package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.taurlom.tnote.data.local.entity.CaldavTombstoneEntity

/**
 * Очередь серверных удалений (этап 2): записи живут от локального
 * удаления до подтверждения сервером, sync между ними удаление не теряет.
 * Идентичность строки — пара (uid, accountId): тот же UID у двух подписок
 * — два разных намерения, каждое доезжает до своего сервера само.
 */
@Dao
interface CaldavTombstoneDao {

    /** Отложенные удаления аккаунта — push-шаг движка. */
    @Query("SELECT * FROM caldav_tombstones WHERE accountId = :accountId ORDER BY deletedAt ASC")
    suspend fun getAllFor(accountId: Long): List<CaldavTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: CaldavTombstoneEntity)

    /**
     * Снятие удаления одной идентичности: сервер подтвердил DELETE (204)
     * или сообщил «и так нет» (404); локальное пересоздание (upsert)
     * всегда новее tombstone, а DELETE снёс бы свежий серверный объект.
     * Ограничение по аккаунту обязательно: у заметок UID выводится из даты,
     * поэтому запись другой подписки под тем же UID — это другое намерение,
     * и снимать его нельзя. Возвращает число снятых записей.
     */
    @Query("DELETE FROM caldav_tombstones WHERE uid = :uid AND accountId = :accountId")
    suspend fun deleteForIdentity(uid: String, accountId: Long): Int

    /** Отключение аккаунта: его очередь DELETE гасится целиком. */
    @Query("DELETE FROM caldav_tombstones WHERE accountId = :accountId")
    suspend fun deleteAllFor(accountId: Long): Int
}

package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity

@Dao
interface CaldavDao {

    /** Живой список аккаунтов для настроек. */
    @Query("SELECT * FROM caldav_accounts ORDER BY id ASC")
    fun observeAll(): Flow<List<CaldavAccountEntity>>

    @Query("SELECT * FROM caldav_accounts ORDER BY id ASC")
    suspend fun getAll(): List<CaldavAccountEntity>

    @Query("SELECT * FROM caldav_accounts WHERE id = :id")
    suspend fun getById(id: Long): CaldavAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: CaldavAccountEntity): Long

    @Query("DELETE FROM caldav_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Записывается движком после успешного SYNC. */
    @Query("UPDATE caldav_accounts SET lastSyncAt = :time, lastError = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, time: Long)

    /** Текст ошибки — без стектрейсов: экран показывает как есть. */
    @Query("UPDATE caldav_accounts SET lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?)
}

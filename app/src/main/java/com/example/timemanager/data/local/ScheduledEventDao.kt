package com.example.timemanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.timemanager.data.local.entity.ScheduledEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledEventDao {

    @Query(
        "SELECT * FROM scheduled_events WHERE eventDate LIKE :monthPrefix " +
            "ORDER BY time IS NULL ASC, time ASC, position ASC, id ASC"
    )
    fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEventEntity>>

    @Query("SELECT * FROM scheduled_events WHERE type = :type ORDER BY eventDate ASC")
    fun getByType(type: String): Flow<List<ScheduledEventEntity>>

    @Query("SELECT * FROM scheduled_events WHERE id = :id")
    suspend fun getByIdOnce(id: Long): ScheduledEventEntity?

    @Query("SELECT * FROM scheduled_events WHERE alarmEnabled = 1 AND time IS NOT NULL")
    suspend fun getAllWithAlarm(): List<ScheduledEventEntity>

    @Query("DELETE FROM scheduled_events WHERE eventDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM scheduled_events")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ScheduledEventEntity): Long

    @Update
    suspend fun update(event: ScheduledEventEntity)

    @Delete
    suspend fun delete(event: ScheduledEventEntity)
}

package com.example.timemanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.timemanager.data.local.entity.CalendarTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarTaskDao {

    @Query("SELECT * FROM calendar_tasks WHERE eventDate = :date ORDER BY position ASC, id ASC")
    fun getByDate(date: String): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks WHERE eventDate LIKE :monthPrefix ORDER BY position ASC, id ASC")
    fun getByMonthPrefix(monthPrefix: String): Flow<List<CalendarTaskEntity>>

    @Query("DELETE FROM calendar_tasks WHERE eventDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM calendar_tasks")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: CalendarTaskEntity): Long

    @Update
    suspend fun update(task: CalendarTaskEntity)

    @Delete
    suspend fun delete(task: CalendarTaskEntity)
}

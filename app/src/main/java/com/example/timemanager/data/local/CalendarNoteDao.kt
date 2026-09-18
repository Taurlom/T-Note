package com.example.timemanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timemanager.data.local.entity.CalendarNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarNoteDao {

    @Query("SELECT * FROM calendar_notes WHERE eventDate = :date")
    fun getByDate(date: String): Flow<List<CalendarNoteEntity>>

    @Query("SELECT * FROM calendar_notes WHERE eventDate = :date")
    suspend fun getByDateOnce(date: String): CalendarNoteEntity?

    @Query("SELECT * FROM calendar_notes WHERE eventDate LIKE :monthPrefix")
    fun getByMonthPrefix(monthPrefix: String): Flow<List<CalendarNoteEntity>>

    @Query("DELETE FROM calendar_notes WHERE eventDate = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM calendar_notes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: CalendarNoteEntity): Long

    @Delete
    suspend fun delete(note: CalendarNoteEntity)
}

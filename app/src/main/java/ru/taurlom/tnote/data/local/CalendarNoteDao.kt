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

    @Query("DELETE FROM calendar_notes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: CalendarNoteEntity): Long
}

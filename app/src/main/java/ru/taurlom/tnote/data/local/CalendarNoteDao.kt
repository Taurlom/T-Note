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
}

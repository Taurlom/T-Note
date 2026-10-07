package ru.taurlom.tnote.domain.repository

import ru.taurlom.tnote.domain.model.CalendarNote
import kotlinx.coroutines.flow.Flow

interface CalendarRepository {

    fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>>

    suspend fun saveNote(note: CalendarNote)
    suspend fun deleteDayData(date: String)

    suspend fun clearCalendar()
}

package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.CalendarNote

interface CalendarRepository {

    fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>>

    suspend fun saveNote(note: CalendarNote)
    suspend fun deleteDayData(date: String)

    suspend fun clearCalendar()
}

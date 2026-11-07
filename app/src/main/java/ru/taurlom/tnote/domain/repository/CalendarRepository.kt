package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.CalendarNote

/** Итог удаления прошедшего: сколько удалено событий и календарных заметок. */
data class PastCalendarCleanup(val events: Int, val notes: Int)

interface CalendarRepository {

    fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>>

    suspend fun saveNote(note: CalendarNote)
    suspend fun deleteDayData(date: String)

    /**
     * Удаляет всё, что раньше сегодняшнего дня: календарные заметки и
     * события без будущих вхождений (см. [ru.taurlom.tnote.domain.model.ScheduledEvent.isOverBefore]).
     * Возвращает счётчики для подтверждения пользователю.
     */
    suspend fun deletePast(): PastCalendarCleanup

    suspend fun clearCalendar()
}

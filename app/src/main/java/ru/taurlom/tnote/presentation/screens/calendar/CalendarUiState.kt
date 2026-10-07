package ru.taurlom.tnote.presentation.screens.calendar

import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.model.ScheduledEvent

data class CalendarUiState(
    val yearMonth: CalendarYearMonth = currentYearMonth(),
    val notes: Map<String, CalendarNote> = emptyMap(),
    val events: Map<String, List<ScheduledEvent>> = emptyMap(),
    /** ISO-даты, помеченные событием «Выходной» (глобально, все месяцы). */
    val weekendDates: Set<String> = emptySet(),
)

fun currentYearMonth(): CalendarYearMonth {
    val calendar = java.util.Calendar.getInstance()
    return CalendarYearMonth(
        year = calendar.get(java.util.Calendar.YEAR),
        month = calendar.get(java.util.Calendar.MONTH) + 1,
    )
}

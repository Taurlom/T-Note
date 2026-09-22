package com.example.timemanager.presentation.screens.calendar

import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.ScheduledEvent

data class CalendarUiState(
    val yearMonth: CalendarYearMonth = currentYearMonth(),
    val notes: Map<String, CalendarNote> = emptyMap(),
    val events: Map<String, List<ScheduledEvent>> = emptyMap(),
    /** ISO-даты, помеченные событием «Выходной» (глобально, все месяцы). */
    val weekendDates: Set<String> = emptySet()
)

fun currentYearMonth(): CalendarYearMonth {
    val calendar = java.util.Calendar.getInstance()
    return CalendarYearMonth(
        year = calendar.get(java.util.Calendar.YEAR),
        month = calendar.get(java.util.Calendar.MONTH) + 1
    )
}

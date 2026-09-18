package com.example.timemanager.presentation.screens.calendar

import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.CalendarTask

data class CalendarUiState(
    val yearMonth: CalendarYearMonth = currentYearMonth(),
    val notes: Map<String, CalendarNote> = emptyMap(),
    val tasks: Map<String, List<CalendarTask>> = emptyMap(),
    val isLoading: Boolean = true
)

fun currentYearMonth(): CalendarYearMonth {
    val calendar = java.util.Calendar.getInstance()
    return CalendarYearMonth(
        year = calendar.get(java.util.Calendar.YEAR),
        month = calendar.get(java.util.Calendar.MONTH) + 1
    )
}

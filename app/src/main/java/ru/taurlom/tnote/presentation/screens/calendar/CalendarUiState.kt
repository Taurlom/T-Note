package ru.taurlom.tnote.presentation.screens.calendar

import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.model.ScheduledEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Режим отображения календаря — вкладки вверху экрана. */
enum class CalendarViewMode { MONTH, WEEK, DAY }

data class CalendarUiState(
    val mode: CalendarViewMode = CalendarViewMode.MONTH,
    val yearMonth: CalendarYearMonth = currentYearMonth(),
    /** Опорная дата режимов «неделя» и «день». */
    val anchorDate: LocalDate = LocalDate.now(),
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

/** Диапазон дат, который показан на экране в текущем режиме. */
fun CalendarUiState.visibleDateRange(): Pair<LocalDate, LocalDate> = when (mode) {
    CalendarViewMode.MONTH -> {
        val month = yearMonth.toYearMonth()
        month.atDay(1) to month.atEndOfMonth()
    }

    CalendarViewMode.WEEK -> {
        // Неделя всегда с понедельника — как и сетка месяца в проекте.
        val start = anchorDate.with(DayOfWeek.MONDAY)
        start to start.plusDays(6)
    }

    CalendarViewMode.DAY -> anchorDate to anchorDate
}

/**
 * Месяцы, покрывающие показанный диапазон. Неделя может переходить через
 * границу месяца, поэтому данных может быть два месяца — иначе дни по краям
 * недели остались бы без событий.
 */
fun CalendarUiState.visibleYearMonths(): List<YearMonth> {
    val (start, end) = visibleDateRange()
    val last = YearMonth.from(end)
    return generateSequence(YearMonth.from(start)) { month ->
        if (month < last) month.plusMonths(1) else null
    }.toList()
}

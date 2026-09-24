package com.example.timemanager.presentation.screens.calendar

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CalendarYearMonth(val year: Int, val month: Int)

data class CalendarDate(val year: Int, val month: Int, val day: Int) {
    fun toIsoString(): String = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
}

fun CalendarYearMonth.toDisplayName(locale: Locale): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    return SimpleDateFormat("LLLL yyyy", locale)
        .format(calendar.time)
        .replaceFirstChar { it.titlecase(locale) }
}

fun CalendarDate.toDisplayName(locale: Locale): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
    }
    // Locale передают из конфигурации контекста: per-app язык на Android < 13
    // не меняет Locale.getDefault, а конфигурацию — меняет.
    return String.format(locale, "%1\$te %1\$tB %1\$tY", calendar)
        .replaceFirstChar { it.titlecase(locale) }
}

fun CalendarYearMonth.plusMonths(delta: Int): CalendarYearMonth {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, delta)
    }
    return CalendarYearMonth(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH) + 1
    )
}

fun CalendarYearMonth.daysInMonth(): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
}

fun CalendarYearMonth.firstDayOfWeekOffset(): Int {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    // Calendar.MONDAY = 2, SUNDAY = 1
    return if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
}

fun CalendarYearMonth.monthPrefix(): String =
    String.format(Locale.US, "%04d-%02d%%", year, month)

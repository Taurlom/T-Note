package com.example.timemanager.presentation.screens.calendar

import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarDayDialogTest {

    private val today = LocalDate.parse("2026-10-02")

    private fun event(type: ScheduledEventType, date: String) =
        ScheduledEvent(date = date, title = "Тест", type = type)

    @Test
    fun `regular event before today is past`() {
        assertTrue(event(ScheduledEventType.REGULAR, "2026-10-01").isPastOccurrence(today))
    }

    @Test
    fun `regular event today is not past`() {
        assertFalse(event(ScheduledEventType.REGULAR, "2026-10-02").isPastOccurrence(today))
    }

    @Test
    fun `regular event in future is not past`() {
        assertFalse(event(ScheduledEventType.REGULAR, "2026-10-03").isPastOccurrence(today))
    }

    @Test
    fun `birthday never becomes past`() {
        assertFalse(event(ScheduledEventType.BIRTHDAY, "1990-01-01").isPastOccurrence(today))
    }

    @Test
    fun `weekend mark never becomes past`() {
        assertFalse(event(ScheduledEventType.WEEKEND, "2026-10-01").isPastOccurrence(today))
    }

    @Test
    fun `event with broken date is not past`() {
        assertFalse(event(ScheduledEventType.REGULAR, "не дата").isPastOccurrence(today))
    }
}

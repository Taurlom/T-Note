package ru.taurlom.tnote.presentation.screens.calendar

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarDayDialogTest {

    private val today = LocalDate.parse("2026-10-02")

    private fun event(type: ScheduledEventType, date: String) =
        ScheduledEvent(date = date, title = "Тест", type = type)

    private fun repeating(anchor: String) = ScheduledEvent(
        date = anchor,
        title = "Тест",
        type = ScheduledEventType.REPEATING,
        repeatIntervalDays = 1
    )

    // Обычные события: день показа совпадает с датой события.

    @Test
    fun `regular event shown on a past day is past`() {
        val day = LocalDate.parse("2026-10-01")
        assertTrue(event(ScheduledEventType.REGULAR, "2026-10-01").isPastOccurrence(day, today))
    }

    @Test
    fun `regular event shown today is not past`() {
        assertFalse(event(ScheduledEventType.REGULAR, "2026-10-02").isPastOccurrence(today, today))
    }

    @Test
    fun `regular event shown on a future day is not past`() {
        val day = LocalDate.parse("2026-10-03")
        assertFalse(event(ScheduledEventType.REGULAR, "2026-10-03").isPastOccurrence(day, today))
    }

    // Повторяющиеся: зачёркивается показанное вхождение, а не якорь.

    @Test
    fun `repeating event with past anchor is not past on a future day`() {
        // Якорь в прошлом, но диалог открыт на будущий день — вхождение впереди.
        val futureDay = LocalDate.parse("2026-10-10")
        assertFalse(repeating("2026-01-01").isPastOccurrence(futureDay, today))
    }

    @Test
    fun `repeating event with past anchor is past on a past day`() {
        val pastDay = LocalDate.parse("2026-09-15")
        assertTrue(repeating("2026-01-01").isPastOccurrence(pastDay, today))
    }

    // Дни рождения и «Выходной» не зачёркиваются никогда.

    @Test
    fun `birthday never becomes past`() {
        val pastDay = LocalDate.parse("2026-09-15")
        assertFalse(event(ScheduledEventType.BIRTHDAY, "1990-09-15").isPastOccurrence(pastDay, today))
    }

    @Test
    fun `weekend mark never becomes past`() {
        val pastDay = LocalDate.parse("2026-09-15")
        assertFalse(event(ScheduledEventType.WEEKEND, "2026-09-15").isPastOccurrence(pastDay, today))
    }
}

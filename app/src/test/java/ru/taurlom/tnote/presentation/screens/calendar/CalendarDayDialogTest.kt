package ru.taurlom.tnote.presentation.screens.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.CaldavProvider
import ru.taurlom.tnote.domain.model.RepeatUnit
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.time.LocalDate

class CalendarDayDialogTest {

    private val today = LocalDate.parse("2026-10-02")

    private fun event(type: ScheduledEventType, date: String) = ScheduledEvent(date = date, title = "Тест", type = type)

    private fun repeating(anchor: String) = ScheduledEvent(
        date = anchor,
        title = "Тест",
        type = ScheduledEventType.REPEATING,
        repeatUnit = RepeatUnit.DAYS,
        repeatInterval = 1,
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

    // Бейдж источника: импортированное событие с припиской подписке
    // показывает провайдера (Яндекс/Google), свои и отвязанные — ничего.

    private fun imported(caldavAccountId: Long?, remoteImported: Boolean) = ScheduledEvent(
        date = "2026-10-02",
        title = "Тест",
        type = ScheduledEventType.REGULAR,
        caldavAccountId = caldavAccountId,
        remoteImported = remoteImported,
    )

    @Test
    fun `imported attributed event shows provider label`() {
        val row = imported(caldavAccountId = 1L, remoteImported = true)
        assertEquals(R.string.sync_badge_yandex, eventSourceLabel(row, CaldavProvider.YANDEX))
        assertEquals(R.string.sync_badge_google, eventSourceLabel(row, CaldavProvider.GOOGLE))
    }

    @Test
    fun `own event pushed to subscription shows no label`() {
        assertNull(eventSourceLabel(imported(caldavAccountId = 1L, remoteImported = false), CaldavProvider.YANDEX))
    }

    @Test
    fun `event kept after disconnect shows no label`() {
        assertNull(eventSourceLabel(imported(caldavAccountId = null, remoteImported = true), CaldavProvider.YANDEX))
    }

    @Test
    fun `no connected provider shows no label`() {
        assertNull(eventSourceLabel(imported(caldavAccountId = 1L, remoteImported = true), null))
    }
}

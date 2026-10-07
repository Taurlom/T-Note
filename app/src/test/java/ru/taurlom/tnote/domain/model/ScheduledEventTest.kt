package ru.taurlom.tnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduledEventTest {

    private fun repeating(
        anchor: String = "2026-01-01",
        interval: Int? = 3,
        repeatDays: Int = 0
    ) = ScheduledEvent(
        date = anchor,
        title = "Тест",
        type = ScheduledEventType.REPEATING,
        repeatIntervalDays = interval,
        repeatDays = repeatDays
    )

    // occursOn

    @Test
    fun `non-repeating event never occurs via occursOn`() {
        val event = ScheduledEvent(date = "2026-01-01", title = "Разовое")
        assertFalse(event.occursOn(LocalDate.parse("2026-01-01")))
    }

    @Test
    fun `anchor day itself is an occurrence`() {
        assertTrue(repeating().occursOn(LocalDate.parse("2026-01-01")))
    }

    @Test
    fun `date before anchor never occurs`() {
        assertFalse(repeating().occursOn(LocalDate.parse("2025-12-31")))
    }

    @Test
    fun `occurs exactly on interval multiples`() {
        val event = repeating(interval = 3)
        assertTrue(event.occursOn(LocalDate.parse("2026-01-04"))) // +3 дня
        assertTrue(event.occursOn(LocalDate.parse("2026-01-07"))) // +6 дней
        assertFalse(event.occursOn(LocalDate.parse("2026-01-05"))) // +4 дня
    }

    @Test
    fun `repeatDays limits the whole repetition window`() {
        val event = repeating(interval = 1, repeatDays = 10)
        assertTrue(event.occursOn(LocalDate.parse("2026-01-10"))) // 9-й день из 10
        assertFalse(event.occursOn(LocalDate.parse("2026-01-11"))) // 10-й день — уже за окном
    }

    @Test
    fun `zero repeatDays means unlimited repetition`() {
        val event = repeating(interval = 7, repeatDays = 0)
        assertTrue(event.occursOn(LocalDate.parse("2026-12-31"))) // +364 дня = 52 недели
    }

    @Test
    fun `missing or zero interval never occurs`() {
        assertFalse(repeating(interval = null).occursOn(LocalDate.parse("2026-01-01")))
        assertFalse(repeating(interval = 0).occursOn(LocalDate.parse("2026-01-01")))
    }

    @Test
    fun `broken anchor date never occurs`() {
        assertFalse(repeating(anchor = "не дата").occursOn(LocalDate.parse("2026-01-01")))
    }

    // effectiveBirthdayDate

    @Test
    fun `feb 29 birthday stays on 29th in leap year`() {
        val anchor = LocalDate.parse("2000-02-29")
        assertEquals(2 to 29, ScheduledEvent.effectiveBirthdayDate(anchor, 2024))
    }

    @Test
    fun `feb 29 birthday shifts to 28th in non-leap year`() {
        val anchor = LocalDate.parse("2000-02-29")
        assertEquals(2 to 28, ScheduledEvent.effectiveBirthdayDate(anchor, 2025))
    }

    @Test
    fun `regular birthday date is unchanged in any year`() {
        val anchor = LocalDate.parse("1990-03-15")
        assertEquals(3 to 15, ScheduledEvent.effectiveBirthdayDate(anchor, 2024))
        assertEquals(3 to 15, ScheduledEvent.effectiveBirthdayDate(anchor, 2025))
    }

    // dateOrNull

    @Test
    fun `dateOrNull parses valid date`() {
        val event = ScheduledEvent(date = "2026-01-01", title = "")
        assertEquals(LocalDate.parse("2026-01-01"), event.dateOrNull())
    }

    @Test
    fun `dateOrNull returns null on garbage`() {
        val event = ScheduledEvent(date = "32.13.2026", title = "")
        assertNull(event.dateOrNull())
    }
}

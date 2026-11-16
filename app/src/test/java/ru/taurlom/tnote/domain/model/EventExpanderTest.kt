package ru.taurlom.tnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Развёртка вхождений: правила общие для сетки календаря и сводки дня.
 *
 * Отдельно проверяется событие длиннее суток (у чужих — Google, Яндекс):
 * оно показывается в каждый покрытый день своей частью, а само остаётся
 * одним событием — одна строка базы, один UID на сервере.
 */
class EventExpanderTest {

    private fun day(iso: String): LocalDate = LocalDate.parse(iso)

    /** Событие длиннее суток: 1 марта 10:00 → 5 марта 18:00. */
    private val multiday = ScheduledEvent(
        date = "2026-03-01",
        title = "Отпуск",
        type = ScheduledEventType.REGULAR,
        startMinutes = 10 * 60,
        endMinutes = 18 * 60,
        endDate = "2026-03-05",
    )

    @Test
    fun `multiday event covers every day of its span`() {
        val byDate = EventExpander.forMonth(
            monthEvents = listOf(multiday),
            birthdays = emptyList(),
            repeating = emptyList(),
            year = 2026,
            month = 3,
            today = day("2026-02-20"),
        )
        // Каждый день длительности — отдельный день в сетке.
        (1..5).forEach { dayNumber ->
            val key = "2026-03-%02d".format(dayNumber)
            assertTrue("в сетке нет $key", byDate.containsKey(key))
            assertEquals(multiday, byDate.getValue(key).single())
        }
        // Дней вне длительности события в сетке нет.
        assertTrue(!byDate.containsKey("2026-03-06"))
    }

    @Test
    fun `multiday event shows its own part of each day`() {
        val first = EventExpander.forDate(
            monthEvents = listOf(multiday),
            birthdays = emptyList(),
            repeating = emptyList(),
            date = day("2026-03-03"),
            today = day("2026-02-20"),
        )
        assertEquals(1, first.size)
        // Окно среднего дня — целые сутки.
        assertEquals(0 to 24 * 60, first.single().windowOn(day("2026-03-03")))
    }

    @Test
    fun `window of first day runs to midnight and last day starts at midnight`() {
        // Первый день: от своего начала до полуночи.
        assertEquals((10 * 60) to 24 * 60, multiday.windowOn(day("2026-03-01")))
        // Последний день: от полуночи до своего конца.
        assertEquals(0 to (18 * 60), multiday.windowOn(day("2026-03-05")))
        // День вне длительности события не входит в него.
        assertNull(multiday.windowOn(day("2026-03-06")))
        assertNull(multiday.windowOn(day("2026-02-28")))
    }

    @Test
    fun `single day event keeps its own hours`() {
        val event = ScheduledEvent(
            date = "2026-03-01",
            title = "Созвон",
            type = ScheduledEventType.REGULAR,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60 + 30,
        )
        assertEquals((10 * 60) to (11 * 60 + 30), event.windowOn(day("2026-03-01")))
        assertNull(event.windowOn(day("2026-03-02")))
    }

    /**
     * Конец эксклюзивен (DTEND по RFC 5545): «23:30 1 марта → 00:00
     * 2 марта» идёт только 1-го марта до полуночи. Нулевое окно 00:00–00:00
     * на дне конца — лишний день в сетке, сводке и дайджесте.
     */
    @Test
    fun `event ending at midnight does not cover the end day`() {
        val nightShift = ScheduledEvent(
            date = "2026-03-01",
            title = "Ночная смена",
            type = ScheduledEventType.REGULAR,
            startMinutes = 23 * 60 + 30,
            endMinutes = 0,
            endDate = "2026-03-02",
        )
        // Первый день — до полуночи (day == start, не end).
        assertEquals((23 * 60 + 30) to 24 * 60, nightShift.windowOn(day("2026-03-01")))
        // День конца при полуночном конце — не покрыт вовсе.
        assertNull(nightShift.windowOn(day("2026-03-02")))

        val byDate = EventExpander.forMonth(
            monthEvents = listOf(nightShift),
            birthdays = emptyList(),
            repeating = emptyList(),
            year = 2026,
            month = 3,
            today = day("2026-02-20"),
        )
        assertTrue("1 марта событие видно", byDate.containsKey("2026-03-01"))
        assertTrue("лишний 2 марта в сетке", !byDate.containsKey("2026-03-02"))

        val summary = EventExpander.forDate(
            monthEvents = listOf(nightShift),
            birthdays = emptyList(),
            repeating = emptyList(),
            date = day("2026-03-02"),
            today = day("2026-02-20"),
        )
        assertTrue(summary.isEmpty())
    }

    @Test
    fun `multi-day span ending at midnight covers every day but the boundary`() {
        // «23:30 1 марта → 00:00 3 марта»: покрыты 1-е (до полуночи) и 2-е
        // целиком; 3-е — граница, его нет.
        val event = ScheduledEvent(
            date = "2026-03-01",
            title = "Вахта",
            type = ScheduledEventType.REGULAR,
            startMinutes = 23 * 60 + 30,
            endMinutes = 0,
            endDate = "2026-03-03",
        )
        assertEquals((23 * 60 + 30) to 24 * 60, event.windowOn(day("2026-03-01")))
        assertEquals(0 to 24 * 60, event.windowOn(day("2026-03-02")))
        assertNull(event.windowOn(day("2026-03-03")))

        val byDate = EventExpander.forMonth(
            monthEvents = listOf(event),
            birthdays = emptyList(),
            repeating = emptyList(),
            year = 2026,
            month = 3,
            today = day("2026-02-20"),
        )
        assertEquals(setOf("2026-03-01", "2026-03-02"), byDate.keys)
    }

    @Test
    fun `own event with end before start is invalid`() {
        // Свои события живут в одних сутках: конец раньше начала — ошибка
        // ввода, её не пропускает редактор.
        val invalid = ScheduledEvent(
            date = "2026-03-01",
            title = "Ночная смена",
            type = ScheduledEventType.REGULAR,
            startMinutes = 23 * 60 + 30,
            endMinutes = 30,
        )
        assertTrue(!invalid.hasValidTimeRange())
    }

    @Test
    fun `foreign event with end on a later day is valid`() {
        // У чужих событий конец приходит на другом дне — это длительность,
        // а не ошибка ввода.
        val valid = multiday.copy(startMinutes = 23 * 60 + 30, endMinutes = 30, endDate = "2026-03-02")
        assertTrue(valid.hasValidTimeRange())
    }
}

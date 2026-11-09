package ru.taurlom.tnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduledEventTest {

    private fun repeating(
        anchor: String = "2026-01-01",
        unit: RepeatUnit? = RepeatUnit.DAYS,
        interval: Int = 3,
        weekdays: Set<DayOfWeek> = emptySet(),
        until: String? = null,
    ) = ScheduledEvent(
        date = anchor,
        title = "Тест",
        type = ScheduledEventType.REPEATING,
        repeatUnit = unit,
        repeatInterval = interval,
        repeatWeekdays = weekdays,
        repeatUntil = until,
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
    fun `daily occurs exactly on interval multiples`() {
        val event = repeating(unit = RepeatUnit.DAYS, interval = 3)
        assertTrue(event.occursOn(LocalDate.parse("2026-01-04"))) // +3 дня
        assertTrue(event.occursOn(LocalDate.parse("2026-01-07"))) // +6 дней
        assertFalse(event.occursOn(LocalDate.parse("2026-01-05"))) // +4 дня
    }

    @Test
    fun `daily until cuts occurrences after the boundary day`() {
        // Последний день окна — 10 января включительно.
        val event = repeating(unit = RepeatUnit.DAYS, interval = 3, until = "2026-01-10")
        assertTrue(event.occursOn(LocalDate.parse("2026-01-10")))
        assertFalse(event.occursOn(LocalDate.parse("2026-01-13")))
    }

    @Test
    fun `weekly with empty weekdays repeats on anchor weekday`() {
        // 2026-01-01 — четверг; без BYDAY повтор — по четвергам.
        val event = repeating(anchor = "2026-01-01", unit = RepeatUnit.WEEKS, interval = 1)
        assertTrue(event.occursOn(LocalDate.parse("2026-01-08")))
        assertFalse(event.occursOn(LocalDate.parse("2026-01-05"))) // понедельник
    }

    @Test
    fun `weekly with weekdays occurs on every selected day`() {
        // Аналог Яндекса «повторять по дням недели»: якорь — вторник,
        // выбраны понедельник и среда — вхождения по Пн и Ср с якорной недели.
        val event = repeating(
            anchor = "2026-01-06", // вторник
            unit = RepeatUnit.WEEKS,
            interval = 1,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        assertTrue(event.occursOn(LocalDate.parse("2026-01-06"))) // якорь — всегда вхождение
        assertTrue(event.occursOn(LocalDate.parse("2026-01-07"))) // среда той же недели
        assertTrue(event.occursOn(LocalDate.parse("2026-01-12"))) // понедельник следующей
        assertTrue(event.occursOn(LocalDate.parse("2026-01-14"))) // среда следующей
        assertFalse(event.occursOn(LocalDate.parse("2026-01-13"))) // вторник следующей недели
    }

    @Test
    fun `weekly interval two skips odd weeks`() {
        // Якорь в неделе 0; неделя 1 (2026-01-12) — пропуск, неделя 2 — вхождение.
        val event = repeating(
            anchor = "2026-01-05", // понедельник
            unit = RepeatUnit.WEEKS,
            interval = 2,
            weekdays = setOf(DayOfWeek.MONDAY),
        )
        assertTrue(event.occursOn(LocalDate.parse("2026-01-19"))) // неделя 2
        assertFalse(event.occursOn(LocalDate.parse("2026-01-12"))) // неделя 1
    }

    @Test
    fun `weekly until cuts the tail`() {
        val event = repeating(
            anchor = "2026-01-05",
            unit = RepeatUnit.WEEKS,
            interval = 1,
            weekdays = setOf(DayOfWeek.MONDAY),
            until = "2026-01-19",
        )
        assertTrue(event.occursOn(LocalDate.parse("2026-01-19")))
        assertFalse(event.occursOn(LocalDate.parse("2026-01-26")))
    }

    @Test
    fun `monthly occurs on the anchor day of aligned months`() {
        val event = repeating(anchor = "2026-01-31", unit = RepeatUnit.MONTHS, interval = 1)
        assertTrue(event.occursOn(LocalDate.parse("2026-03-31")))
        assertFalse(event.occursOn(LocalDate.parse("2026-02-28"))) // в феврале 31-го нет — пропущено
        assertFalse(event.occursOn(LocalDate.parse("2026-04-30")))
    }

    @Test
    fun `monthly interval two counts months from anchor`() {
        val event = repeating(anchor = "2026-01-10", unit = RepeatUnit.MONTHS, interval = 2)
        assertTrue(event.occursOn(LocalDate.parse("2026-03-10")))
        assertFalse(event.occursOn(LocalDate.parse("2026-04-10"))) // нечётная фаза
    }

    @Test
    fun `yearly occurs on month and day`() {
        val event = repeating(anchor = "2024-03-15", unit = RepeatUnit.YEARS, interval = 1)
        assertTrue(event.occursOn(LocalDate.parse("2026-03-15")))
        assertFalse(event.occursOn(LocalDate.parse("2026-03-16")))
    }

    @Test
    fun `yearly skips feb 29 in non-leap years`() {
        val event = repeating(anchor = "2024-02-29", unit = RepeatUnit.YEARS, interval = 1)
        assertFalse(event.occursOn(LocalDate.parse("2026-02-28")))
        assertTrue(event.occursOn(LocalDate.parse("2028-02-29")))
    }

    @Test
    fun `missing unit or zero interval never occurs`() {
        assertFalse(repeating(unit = null).occursOn(LocalDate.parse("2026-01-01")))
        assertFalse(repeating(interval = 0).occursOn(LocalDate.parse("2026-01-01")))
    }

    @Test
    fun `broken anchor date never occurs`() {
        assertFalse(repeating(anchor = "не дата").occursOn(LocalDate.parse("2026-01-01")))
    }

    // isOverBefore

    @Test
    fun `regular event in the past is over`() {
        val event = ScheduledEvent(date = "2026-01-01", title = "Разовое")
        assertTrue(event.isOverBefore(LocalDate.parse("2026-06-01")))
        // Сам день сегодня и будущие — не прошедшие.
        assertFalse(event.isOverBefore(LocalDate.parse("2026-01-01")))
        assertFalse(event.isOverBefore(LocalDate.parse("2025-12-31")))
    }

    @Test
    fun `birthday is never over`() {
        val event = ScheduledEvent(date = "1990-01-01", title = "ДР", type = ScheduledEventType.BIRTHDAY)
        assertFalse(event.isOverBefore(LocalDate.parse("2026-06-01")))
    }

    @Test
    fun `unlimited repeating is never over`() {
        // Бессрочное повторение живёт всегда.
        assertFalse(
            repeating(anchor = "2020-01-01", unit = RepeatUnit.DAYS, interval = 7)
                .isOverBefore(LocalDate.parse("2026-06-01")),
        )
    }

    @Test
    fun `repeating with passed until date is over`() {
        assertTrue(
            repeating(anchor = "2026-01-01", unit = RepeatUnit.DAYS, interval = 3, until = "2026-01-10")
                .isOverBefore(LocalDate.parse("2026-06-01")),
        )
        // Граница не достигнута — живое.
        assertFalse(
            repeating(anchor = "2026-01-01", unit = RepeatUnit.DAYS, interval = 3, until = "2026-01-10")
                .isOverBefore(LocalDate.parse("2026-01-10")),
        )
    }

    @Test
    fun `repeating without unit is kept`() {
        // Битое повторение без частоты синтаксически живое: правило не удаляет.
        assertFalse(repeating(anchor = "2020-01-01", unit = null, interval = 1).isOverBefore(LocalDate.parse("2026-06-01")))
    }

    @Test
    fun `broken anchor date is kept`() {
        assertFalse(repeating(anchor = "не дата").isOverBefore(LocalDate.parse("2026-06-01")))
    }

    // Кодировка дней недели (BYDAY)

    @Test
    fun `weekday codes round-trip`() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        val code = ScheduledEvent.weekdaysToCode(weekdays)
        assertEquals("MO,WE,SU", code)
        assertEquals(weekdays, ScheduledEvent.weekdaysFromCode(code))
    }

    @Test
    fun `empty weekday set encodes to null`() {
        assertNull(ScheduledEvent.weekdaysToCode(emptySet()))
        assertEquals(emptySet<DayOfWeek>(), ScheduledEvent.weekdaysFromCode(null))
        assertEquals(emptySet<DayOfWeek>(), ScheduledEvent.weekdaysFromCode(""))
    }

    @Test
    fun `unknown weekday codes are skipped`() {
        assertEquals(setOf(DayOfWeek.TUESDAY), ScheduledEvent.weekdaysFromCode("TU,XX"))
    }

    // Отменённые вхождения (EXDATE)

    @Test
    fun `exdate removes the occurrence from the set`() {
        // Повтор по понедельникам; 30 ноября отменено — этот день пуст.
        val event = repeating(
            anchor = "2026-11-16",
            unit = RepeatUnit.WEEKS,
            interval = 1,
            weekdays = setOf(DayOfWeek.MONDAY),
        ).copy(exdates = setOf(LocalDate.parse("2026-11-30")))
        assertFalse(event.occursOn(LocalDate.parse("2026-11-30")))
        assertTrue(event.occursOn(LocalDate.parse("2026-12-07")))
    }

    @Test
    fun `exdate can cancel even the anchor day`() {
        // RFC 5545: исключения применяются к финальному набору, куда якорь
        // входит так же, как остальные вхождения.
        val event = repeating(
            anchor = "2026-01-06",
            unit = RepeatUnit.WEEKS,
            interval = 1,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        ).copy(exdates = setOf(LocalDate.parse("2026-01-06")))
        assertFalse(event.occursOn(LocalDate.parse("2026-01-06")))
        assertTrue(event.occursOn(LocalDate.parse("2026-01-07")))
    }

    @Test
    fun `isExcludedOn filters anchor-day events too`() {
        val event = ScheduledEvent(
            date = "2026-03-05",
            title = "Срыв",
            exdates = setOf(LocalDate.parse("2026-03-05")),
        )
        assertTrue(event.isExcludedOn(LocalDate.parse("2026-03-05")))
        assertFalse(event.isExcludedOn(LocalDate.parse("2026-03-06")))
    }

    @Test
    fun `exdate codes round-trip sorted and deduped`() {
        val exdates = setOf(LocalDate.parse("2026-03-12"), LocalDate.parse("2026-03-05"))
        val code = ScheduledEvent.exdatesToCode(exdates)
        assertEquals("2026-03-05,2026-03-12", code)
        assertEquals(exdates, ScheduledEvent.exdatesFromCode(code))
    }

    @Test
    fun `empty exdate set encodes to null`() {
        assertNull(ScheduledEvent.exdatesToCode(emptySet()))
        assertEquals(emptySet<LocalDate>(), ScheduledEvent.exdatesFromCode(null))
        assertEquals(emptySet<LocalDate>(), ScheduledEvent.exdatesFromCode(""))
        assertEquals(emptySet<LocalDate>(), ScheduledEvent.exdatesFromCode("мусор"))
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

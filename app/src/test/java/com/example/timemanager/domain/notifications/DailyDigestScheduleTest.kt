package com.example.timemanager.domain.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyDigestScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(isoDate: String, hour: Int, minute: Int = 0): ZonedDateTime =
        LocalDate.parse(isoDate).atTime(hour, minute).atZone(zone)

    // nextDigestTime

    @Test
    fun `before reminder time schedules today`() {
        val next = DailyDigestSchedule.nextDigestTime(at("2026-07-10", 8), 9 * 60)
        assertEquals(at("2026-07-10", 9), next)
    }

    @Test
    fun `after reminder time schedules tomorrow`() {
        val next = DailyDigestSchedule.nextDigestTime(at("2026-07-10", 10), 9 * 60)
        assertEquals(at("2026-07-11", 9), next)
    }

    @Test
    fun `exactly at reminder time schedules tomorrow - now is handled by today's worker`() {
        // Иначе задача, поставленная «на завтра» в 09:00:00, встала бы
        // на сегодня, и сводка задублировалась бы.
        val next = DailyDigestSchedule.nextDigestTime(at("2026-07-10", 9), 9 * 60)
        assertEquals(at("2026-07-11", 9), next)
    }

    @Test
    fun `non-multiple-of-hour reminder time is exact`() {
        val next = DailyDigestSchedule.nextDigestTime(at("2026-07-10", 7), 8 * 60 + 45)
        assertEquals(at("2026-07-10", 8, 45), next)
    }

    // isDigestTimeReached

    @Test
    fun `reached flag flips at reminder time`() {
        assertTrue(DailyDigestSchedule.isDigestTimeReached(at("2026-07-10", 9), 9 * 60))
        assertFalse(DailyDigestSchedule.isDigestTimeReached(at("2026-07-10", 8, 59), 9 * 60))
    }

    @Test
    fun `late evening is reached for morning reminder`() {
        // Воркер, отложенный doze до вечера, всё ещё имеет право показать
        // сводку сегодняшнего дня.
        assertTrue(DailyDigestSchedule.isDigestTimeReached(at("2026-07-10", 21), 9 * 60))
    }

    @Test
    fun `midnight reminder works`() {
        assertTrue(DailyDigestSchedule.isDigestTimeReached(at("2026-07-10", 0, 30), 0))
        val next = DailyDigestSchedule.nextDigestTime(at("2026-07-10", 0, 30), 0)
        assertEquals(at("2026-07-11", 0), next)
    }

    // Перевод часов: пользователь просил «в 9:00» — значит, в 9:00 по
    // новому настенному времени, а не «спустя 9 реальных часов от полуночи»
    // (ZonedDateTime.plusMinutes дал бы как раз второе — 10:00 в этот день).

    @Test
    fun `spring forward day keeps wall-clock time`() {
        // В Берлине 29.03.2026 в 02:00 часы прыгают на 03:00.
        val berlin = ZoneId.of("Europe/Berlin")
        val now = LocalDate.parse("2026-03-28").atTime(10, 0).atZone(berlin)
        val next = DailyDigestSchedule.nextDigestTime(now, 9 * 60)
        assertEquals(LocalDate.parse("2026-03-29").atTime(9, 0).atZone(berlin), next)
    }

    @Test
    fun `fall back day keeps wall-clock time`() {
        // В Берлине 25.10.2026 в 03:00 часы откатываются на 02:00.
        val berlin = ZoneId.of("Europe/Berlin")
        val now = LocalDate.parse("2026-10-24").atTime(10, 0).atZone(berlin)
        val next = DailyDigestSchedule.nextDigestTime(now, 9 * 60)
        assertEquals(LocalDate.parse("2026-10-25").atTime(9, 0).atZone(berlin), next)
    }
}

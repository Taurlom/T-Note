package ru.taurlom.tnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Часы события в 24-часовом виде: минуты от полуночи → «10:00». Формат не
 * зависит от локали устройства — одинаково в интерфейсе, в уведомлении и на
 * сервере.
 */
class EventClockTest {

    @Test
    fun `minutes render as 24 hour clock`() {
        assertEquals("00:00", formatEventClock(0))
        assertEquals("09:05", formatEventClock(9 * 60 + 5))
        assertEquals("10:00", formatEventClock(10 * 60))
        assertEquals("23:59", formatEventClock(23 * 60 + 59))
    }
}

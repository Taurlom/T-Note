package ru.taurlom.tnote.presentation.screens.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Валидация времени события в редакторе: «Завершение» не может быть
 * раньше «Начала» — такое событие сохранить нельзя.
 *
 * Закон доменный ([ru.taurlom.tnote.domain.model.ScheduledEvent.hasValidTimeRange]):
 * у чужого события длиннее суток конец приходит на другой день — это его
 * длительность, а не ошибка.
 */
class EventTimeValidationTest {

    @Test
    fun `end before start is invalid`() {
        assertTrue(
            isTimeRangeInvalid(
                date = "2026-03-01",
                startMinutes = 10 * 60,
                endMinutes = 9 * 60,
            ),
        )
    }

    @Test
    fun `equal start and end are valid`() {
        assertFalse(
            isTimeRangeInvalid(
                date = "2026-03-01",
                startMinutes = 10 * 60,
                endMinutes = 10 * 60,
            ),
        )
    }

    @Test
    fun `only one side of the range is valid`() {
        assertFalse(
            isTimeRangeInvalid(
                date = "2026-03-01",
                startMinutes = 10 * 60,
                endMinutes = null,
            ),
        )
        assertFalse(
            isTimeRangeInvalid(
                date = "2026-03-01",
                startMinutes = null,
                endMinutes = 10 * 60,
            ),
        )
    }

    @Test
    fun `foreign event with end on a later day is valid`() {
        // Конец на другом дне — это длительность чужого события, а не
        // ошибка ввода: собственных много-дневных событий в редакторе нет.
        assertFalse(
            isTimeRangeInvalid(
                date = "2026-03-01",
                startMinutes = 23 * 60 + 30,
                endMinutes = 30,
                endDate = "2026-03-02",
            ),
        )
    }
}

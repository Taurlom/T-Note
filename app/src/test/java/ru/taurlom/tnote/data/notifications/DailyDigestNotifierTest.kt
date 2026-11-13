package ru.taurlom.tnote.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType

/**
 * Строка события в уведомлении-сводке: с часами — «10:00 – 11:30 Встреча»,
 * без времени — просто название.
 */
@RunWith(RobolectricTestRunner::class)
class DailyDigestNotifierTest {

    private val notifier = DailyDigestNotifier(
        ApplicationProvider.getApplicationContext<Context>(),
    )

    @Test
    fun `event with clock shows it before the title`() {
        val event = ScheduledEvent(
            date = "2026-11-16",
            title = "Созвон",
            type = ScheduledEventType.REGULAR,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60 + 30,
        )
        assertEquals("10:00 – 11:30 Созвон", notifier.eventLine(event))
    }

    @Test
    fun `event with start only shows the start`() {
        val event = ScheduledEvent(
            date = "2026-11-16",
            title = "Созвон",
            type = ScheduledEventType.REGULAR,
            startMinutes = 9 * 60,
        )
        assertEquals("09:00 Созвон", notifier.eventLine(event))
    }

    @Test
    fun `all day event keeps title only`() {
        val event = ScheduledEvent(
            date = "2026-11-16",
            title = "Отпуск",
            type = ScheduledEventType.REGULAR,
        )
        assertEquals("Отпуск", notifier.eventLine(event))
    }
}

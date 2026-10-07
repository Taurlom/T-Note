package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.taurlom.tnote.domain.notifications.ReminderScheduler
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class SetRemindersEnabledUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now = LocalDate.parse("2026-07-10").atTime(8, 0).atZone(zone)
    private val clock: Clock = Clock.fixed(now.toInstant(), zone)

    @Test
    fun `enabling writes the setting and starts the chain`() = runBlocking {
        val settings = FakeSettingsRepository(remindersEnabled = false)
        val scheduler = RecordingReminderScheduler()
        val useCase = SetRemindersEnabledUseCase(
            settings,
            ScheduleNextDailyDigestUseCase(settings, scheduler, clock),
        )
        useCase(true)
        assertEquals(true, settings.remindersEnabled.first())
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(0, scheduler.cancelled)
    }

    @Test
    fun `disabling writes the setting and cancels the chain`() = runBlocking {
        val settings = FakeSettingsRepository(remindersEnabled = true)
        val scheduler = RecordingReminderScheduler()
        val useCase = SetRemindersEnabledUseCase(
            settings,
            ScheduleNextDailyDigestUseCase(settings, scheduler, clock),
        )
        useCase(false)
        assertEquals(false, settings.remindersEnabled.first())
        assertEquals(0, scheduler.scheduled.size)
        assertEquals(1, scheduler.cancelled)
    }
}

class SetReminderTimeUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now = LocalDate.parse("2026-07-10").atTime(8, 0).atZone(zone)
    private val clock: Clock = Clock.fixed(now.toInstant(), zone)

    @Test
    fun `time change reschedules to the new time`() = runBlocking {
        val settings = FakeSettingsRepository(
            remindersEnabled = true,
            reminderTimeMinutes = 9 * 60,
        )
        val scheduler = RecordingReminderScheduler()
        val useCase = SetReminderTimeUseCase(
            settings,
            ScheduleNextDailyDigestUseCase(settings, scheduler, clock),
        )
        useCase(11 * 60 + 15)
        assertEquals(11 * 60 + 15, settings.reminderTimeMinutes.first())
        val expected = LocalDate.parse("2026-07-10").atTime(11, 15).atZone(zone)
            .toInstant().toEpochMilli()
        assertEquals(listOf(expected), scheduler.scheduled.map { it.first })
        assertEquals(listOf(ReminderScheduler.Mode.RESCHEDULE), scheduler.scheduled.map { it.second })
    }

    @Test
    fun `time change with reminders off cancels instead of scheduling`() = runBlocking {
        // Цепочка выключена — новая задача не заводится, висячая гасится.
        val settings = FakeSettingsRepository(remindersEnabled = false)
        val scheduler = RecordingReminderScheduler()
        val useCase = SetReminderTimeUseCase(
            settings,
            ScheduleNextDailyDigestUseCase(settings, scheduler, clock),
        )
        useCase(12 * 60)
        assertEquals(0, scheduler.scheduled.size)
        assertEquals(1, scheduler.cancelled)
    }
}

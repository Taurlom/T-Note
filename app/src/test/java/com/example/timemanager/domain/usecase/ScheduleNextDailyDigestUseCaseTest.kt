package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.AppFont
import com.example.timemanager.domain.model.ThemeKind
import com.example.timemanager.domain.notifications.DailyDigestSchedule
import com.example.timemanager.domain.notifications.ReminderScheduler
import com.example.timemanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ScheduleNextDailyDigestUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val now = LocalDate.parse("2026-07-10").atTime(8, 0).atZone(zone)
    private val clock: Clock = Clock.fixed(now.toInstant(), zone)

    @Test
    fun `disabled reminders cancel the chain instead of scheduling`() = runBlocking {
        val scheduler = RecordingReminderScheduler()
        val useCase = ScheduleNextDailyDigestUseCase(
            FakeSettingsRepository(remindersEnabled = false),
            scheduler,
            clock
        )
        useCase(ReminderScheduler.Mode.IF_IDLE)
        assertEquals(0, scheduler.scheduled.size)
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun `enabled schedules next digest instant and forwards mode`() = runBlocking {
        val scheduler = RecordingReminderScheduler()
        val useCase = ScheduleNextDailyDigestUseCase(
            FakeSettingsRepository(remindersEnabled = true, reminderTimeMinutes = 9 * 60),
            scheduler,
            clock
        )
        useCase(ReminderScheduler.Mode.AFTER_CURRENT)
        // 08:00 → сегодня в 09:00.
        val expected = LocalDate.parse("2026-07-10").atTime(9, 0).atZone(zone)
            .toInstant().toEpochMilli()
        assertEquals(listOf(expected to ReminderScheduler.Mode.AFTER_CURRENT), scheduler.scheduled)
    }

    @Test
    fun `past time schedules tomorrow`() = runBlocking {
        val scheduler = RecordingReminderScheduler()
        val useCase = ScheduleNextDailyDigestUseCase(
            FakeSettingsRepository(remindersEnabled = true, reminderTimeMinutes = 7 * 60),
            scheduler,
            clock
        )
        useCase(ReminderScheduler.Mode.RESCHEDULE)
        val expected = LocalDate.parse("2026-07-11").atTime(7, 0).atZone(zone)
            .toInstant().toEpochMilli()
        assertEquals(listOf(expected to ReminderScheduler.Mode.RESCHEDULE), scheduler.scheduled)
    }

    @Test
    fun `settings are re-read on every call - time change applies immediately`() = runBlocking {
        val settings = FakeSettingsRepository(remindersEnabled = true, reminderTimeMinutes = 9 * 60)
        val scheduler = RecordingReminderScheduler()
        val useCase = ScheduleNextDailyDigestUseCase(settings, scheduler, clock)
        useCase(ReminderScheduler.Mode.RESCHEDULE)
        settings.setReminderTimeMinutes(10 * 60 + 30)
        useCase(ReminderScheduler.Mode.RESCHEDULE)
        val expected = LocalDate.parse("2026-07-10").atTime(10, 30).atZone(zone)
            .toInstant().toEpochMilli()
        assertEquals(expected, scheduler.scheduled.last().first)
    }
}

/** Настройки в памяти: то, что записали, то и прочитается. */
internal class FakeSettingsRepository(
    remindersEnabled: Boolean = false,
    reminderTimeMinutes: Int = DailyDigestSchedule.DEFAULT_REMINDER_MINUTES
) : SettingsRepository {
    private val enabled = MutableStateFlow(remindersEnabled)
    private val timeMinutes = MutableStateFlow(reminderTimeMinutes)

    override val selectedFont: Flow<AppFont> = flowOf(AppFont.PT_SANS)
    override suspend fun setSelectedFont(font: AppFont) = Unit
    override val selectedTheme: Flow<ThemeKind> = flowOf(ThemeKind.DARK)
    override suspend fun setSelectedTheme(theme: ThemeKind) = Unit
    override val visibleSections: Flow<List<String>> = flowOf(emptyList())
    override suspend fun setVisibleSections(sections: List<String>) = Unit
    override val remindersEnabled: Flow<Boolean> = enabled
    override suspend fun setRemindersEnabled(newEnabled: Boolean) {
        enabled.value = newEnabled
    }
    override val reminderTimeMinutes: Flow<Int> = timeMinutes
    override suspend fun setReminderTimeMinutes(minutes: Int) {
        timeMinutes.value = minutes
    }
}

/** Планировщик-самописец: ничего не планирует, только запоминает вызовы. */
internal class RecordingReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<Pair<Long, ReminderScheduler.Mode>>()
    var cancelled = 0

    override suspend fun scheduleNextDigest(atEpochMillis: Long, mode: ReminderScheduler.Mode) {
        scheduled += atEpochMillis to mode
    }

    override suspend fun cancelScheduledDigests() {
        cancelled++
    }
}

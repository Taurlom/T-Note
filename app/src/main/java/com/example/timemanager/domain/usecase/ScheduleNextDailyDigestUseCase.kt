package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.notifications.DailyDigestSchedule
import com.example.timemanager.domain.notifications.ReminderScheduler
import com.example.timemanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Единственная точка, ставящая задачу сводки: включение и смена времени
 * в настройках, финал воркера (себе на завтра), старт приложения
 * (самовосстановление цепочки). Перечитывает настройки на каждом вызове:
 * пользователь мог перенести время, пока задача уже висела.
 *
 * Выключенные напоминания гасят цепочку (cancel): «тихое» расписание,
 * которое всё равно ничего не постит, не оставляем — иначе IF_IDLE при
 * старте приложения оживлял бы задачу, выключенную пользователем.
 */
class ScheduleNextDailyDigestUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val clock: Clock
) {
    suspend operator fun invoke(mode: ReminderScheduler.Mode) {
        if (!settingsRepository.remindersEnabled.first()) {
            reminderScheduler.cancelScheduledDigests()
            return
        }
        val minutes = settingsRepository.reminderTimeMinutes.first()
        val next = DailyDigestSchedule.nextDigestTime(ZonedDateTime.now(clock), minutes)
        reminderScheduler.scheduleNextDigest(next.toInstant().toEpochMilli(), mode)
    }
}

package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.notifications.ReminderScheduler
import com.example.timemanager.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Смена времени сводки. Пишет настройку и перепланирует цепочку: новая
 * задача встанет на ближайшее будущее время. Если сводка на сегодня уже
 * пришла — новая придёт завтра; задним числом не будим.
 */
class SetReminderTimeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduleNextDailyDigest: ScheduleNextDailyDigestUseCase
) {
    suspend operator fun invoke(minutes: Int) {
        settingsRepository.setReminderTimeMinutes(minutes)
        scheduleNextDailyDigest(ReminderScheduler.Mode.RESCHEDULE)
    }
}

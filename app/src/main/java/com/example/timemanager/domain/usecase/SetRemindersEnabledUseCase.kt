package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.notifications.ReminderScheduler
import com.example.timemanager.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Включение/выключение напоминаний-сводок. Пишет настройку и сразу
 * перестраивает расписание: включение заводит цепочку задач, выключение
 * её гасит (см. ScheduleNextDailyDigestUseCase — выключенное состояние
 * отменяет задачи).
 */
class SetRemindersEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduleNextDailyDigest: ScheduleNextDailyDigestUseCase
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.setRemindersEnabled(enabled)
        scheduleNextDailyDigest(ReminderScheduler.Mode.RESCHEDULE)
    }
}

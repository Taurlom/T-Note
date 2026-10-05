package com.example.timemanager.data.notifications

import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.domain.usecase.GetEventsForDateUseCase
import com.example.timemanager.domain.usecase.ScheduleNextDailyDigestUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Доступ к графу Hilt из воркера. WorkManager создаёт воркеров своей
 * фабрикой рефлексией; альтернатива — @HiltWorker с зависимостью
 * androidx.hilt:hilt-work — потребовала бы перенастройку инициализации
 * WorkManager (Configuration.Provider + вырезание инициализатора из
 * манифеста). Точка входа решает то же самое штатным механизмом Dagger
 * без новых зависимостей.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DailyDigestDependencies {
    fun settingsRepository(): SettingsRepository
    fun eventsForDate(): GetEventsForDateUseCase
    fun scheduleNextDigest(): ScheduleNextDailyDigestUseCase
    fun notifier(): DailyDigestNotifier
}

package ru.taurlom.tnote.data.notifications

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.taurlom.tnote.domain.repository.SettingsRepository
import ru.taurlom.tnote.domain.usecase.GetEventsForDateUseCase
import ru.taurlom.tnote.domain.usecase.ScheduleNextDailyDigestUseCase

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

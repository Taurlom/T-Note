package ru.taurlom.tnote.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Часы для домена. Инъекция вместо прямого ZonedDateTime.now() делает
 * расчёт расписания напоминаний тестируемым: тесты подставляют
 * фиксированное время (Clock.fixed).
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}

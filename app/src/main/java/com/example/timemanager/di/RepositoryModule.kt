package com.example.timemanager.di

import com.example.timemanager.data.repository.CalendarRepositoryImpl
import com.example.timemanager.data.repository.CategoryRepositoryImpl
import com.example.timemanager.data.repository.DocumentRepositoryImpl
import com.example.timemanager.data.repository.ScheduledEventRepositoryImpl
import com.example.timemanager.data.repository.SettingsRepositoryImpl
import com.example.timemanager.data.repository.TaskRepositoryImpl
import com.example.timemanager.domain.repository.CalendarRepository
import com.example.timemanager.domain.repository.CategoryRepository
import com.example.timemanager.domain.repository.DocumentRepository
import com.example.timemanager.domain.repository.ScheduledEventRepository
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl
    ): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindScheduledEventRepository(
        impl: ScheduledEventRepositoryImpl
    ): ScheduledEventRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        impl: DocumentRepositoryImpl
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}

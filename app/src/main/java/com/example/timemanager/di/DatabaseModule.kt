package com.example.timemanager.di

import android.content.Context
import androidx.room.Room
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.data.local.AppDatabaseMigration
import com.example.timemanager.data.local.CalendarNoteDao
import com.example.timemanager.data.local.CategoryDao
import com.example.timemanager.data.local.DocumentDao
import com.example.timemanager.data.local.ScheduledEventDao
import com.example.timemanager.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "time_manager.db"
        )
            .addMigrations(
                AppDatabaseMigration.MIGRATION_5_6,
                AppDatabaseMigration.MIGRATION_6_7,
                AppDatabaseMigration.MIGRATION_7_8,
                AppDatabaseMigration.MIGRATION_8_9,
                AppDatabaseMigration.MIGRATION_9_10,
                AppDatabaseMigration.MIGRATION_10_11,
                AppDatabaseMigration.MIGRATION_11_12
            )
            // Никакого destructive fallback: отсутствие миграции должно падать
            // loudly на этапе разработки, а не молча стирать пользовательские данные.
            .build()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideCalendarNoteDao(database: AppDatabase): CalendarNoteDao = database.calendarNoteDao()

    @Provides
    fun provideScheduledEventDao(database: AppDatabase): ScheduledEventDao =
        database.scheduledEventDao()

    @Provides
    fun provideDocumentDao(database: AppDatabase): DocumentDao = database.documentDao()
}

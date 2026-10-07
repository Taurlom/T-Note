package ru.taurlom.tnote.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.data.local.AppDatabaseMigration
import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.CategoryDao
import ru.taurlom.tnote.data.local.DocumentDao
import ru.taurlom.tnote.data.local.NoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.TaskDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DB_NAME,
    )
        .addMigrations(
            AppDatabaseMigration.MIGRATION_5_6,
            AppDatabaseMigration.MIGRATION_6_7,
            AppDatabaseMigration.MIGRATION_7_8,
            AppDatabaseMigration.MIGRATION_8_9,
            AppDatabaseMigration.MIGRATION_9_10,
            AppDatabaseMigration.MIGRATION_10_11,
            AppDatabaseMigration.MIGRATION_11_12,
            AppDatabaseMigration.MIGRATION_12_13,
            AppDatabaseMigration.MIGRATION_13_14,
            AppDatabaseMigration.MIGRATION_14_15,
            AppDatabaseMigration.MIGRATION_15_16,
        )
        // Никакого destructive fallback: отсутствие миграции должно падать
        // loudly на этапе разработки, а не молча стирать пользовательские данные.
        .build()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideCalendarNoteDao(database: AppDatabase): CalendarNoteDao = database.calendarNoteDao()

    @Provides
    fun provideScheduledEventDao(database: AppDatabase): ScheduledEventDao = database.scheduledEventDao()

    @Provides
    fun provideDocumentDao(database: AppDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao = database.noteDao()
}

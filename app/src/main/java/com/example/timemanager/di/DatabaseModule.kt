package com.example.timemanager.di

import android.content.Context
import androidx.room.Room
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.data.local.AppDatabaseMigration
import com.example.timemanager.data.local.CalendarNoteDao
import com.example.timemanager.data.local.CalendarTaskDao
import com.example.timemanager.data.local.CategoryDao
import com.example.timemanager.data.local.DocumentDao
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
            .addMigrations(AppDatabaseMigration.MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideCalendarNoteDao(database: AppDatabase): CalendarNoteDao = database.calendarNoteDao()

    @Provides
    fun provideCalendarTaskDao(database: AppDatabase): CalendarTaskDao = database.calendarTaskDao()

    @Provides
    fun provideDocumentDao(database: AppDatabase): DocumentDao = database.documentDao()
}

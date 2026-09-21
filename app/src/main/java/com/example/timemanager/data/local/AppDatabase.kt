package com.example.timemanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.timemanager.data.local.entity.CalendarNoteEntity
import com.example.timemanager.data.local.entity.CategoryEntity
import com.example.timemanager.data.local.entity.DocumentEntity
import com.example.timemanager.data.local.entity.DocumentPhotoEntity
import com.example.timemanager.data.local.entity.ScheduledEventEntity
import com.example.timemanager.data.local.entity.TaskEntity

@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        CalendarNoteEntity::class,
        ScheduledEventEntity::class,
        DocumentEntity::class,
        DocumentPhotoEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun calendarNoteDao(): CalendarNoteDao
    abstract fun scheduledEventDao(): ScheduledEventDao
    abstract fun documentDao(): DocumentDao
}

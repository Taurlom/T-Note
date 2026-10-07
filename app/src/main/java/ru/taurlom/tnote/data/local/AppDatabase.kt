package ru.taurlom.tnote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.taurlom.tnote.data.local.entity.CalendarNoteEntity
import ru.taurlom.tnote.data.local.entity.CategoryEntity
import ru.taurlom.tnote.data.local.entity.DocumentEntity
import ru.taurlom.tnote.data.local.entity.DocumentPhotoEntity
import ru.taurlom.tnote.data.local.entity.NoteEntity
import ru.taurlom.tnote.data.local.entity.NotePhotoEntity
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.data.local.entity.TaskEntity

@Database(
    entities = [
        CategoryEntity::class,
        TaskEntity::class,
        CalendarNoteEntity::class,
        ScheduledEventEntity::class,
        DocumentEntity::class,
        DocumentPhotoEntity::class,
        NoteEntity::class,
        NotePhotoEntity::class
    ],
    version = 16,
    // Схемы экспортируются в app/schemas (см. ksp-аргумент в build.gradle.kts).
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        /**
         * Имя файла базы — единственный источник истины. Его открывает
         * Room (DatabaseModule) и подменяет резервная копия
         * (BackupRepositoryImpl); раньше литерал жил в двух местах, и
         * рассинхрон сломал бы бэкап молча: экспорт упаковал бы чужой
         * файл, а интеграционные тесты копии закрепляют имя как формат
         * архива и разницы не увидели бы.
         */
        const val DB_NAME = "time_manager.db"
    }

    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun calendarNoteDao(): CalendarNoteDao
    abstract fun scheduledEventDao(): ScheduledEventDao
    abstract fun documentDao(): DocumentDao
    abstract fun noteDao(): NoteDao
}

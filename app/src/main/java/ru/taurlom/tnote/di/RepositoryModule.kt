package ru.taurlom.tnote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.taurlom.tnote.data.backup.BackupRepositoryImpl
import ru.taurlom.tnote.data.local.DocumentPhotoSaver
import ru.taurlom.tnote.data.local.NotePhotoSaver
import ru.taurlom.tnote.data.notifications.WorkManagerReminderScheduler
import ru.taurlom.tnote.data.repository.CalendarRepositoryImpl
import ru.taurlom.tnote.data.repository.CategoryRepositoryImpl
import ru.taurlom.tnote.data.repository.DocumentRepositoryImpl
import ru.taurlom.tnote.data.repository.NoteRepositoryImpl
import ru.taurlom.tnote.data.repository.ScheduledEventRepositoryImpl
import ru.taurlom.tnote.data.repository.SettingsRepositoryImpl
import ru.taurlom.tnote.data.repository.TaskRepositoryImpl
import ru.taurlom.tnote.data.share.ListShareRepositoryImpl
import ru.taurlom.tnote.domain.notifications.ReminderScheduler
import ru.taurlom.tnote.domain.repository.BackupRepository
import ru.taurlom.tnote.domain.repository.CalendarRepository
import ru.taurlom.tnote.domain.repository.CategoryRepository
import ru.taurlom.tnote.domain.repository.DocumentRepository
import ru.taurlom.tnote.domain.repository.ListShareRepository
import ru.taurlom.tnote.domain.repository.NoteRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import ru.taurlom.tnote.domain.repository.SettingsRepository
import ru.taurlom.tnote.domain.repository.TaskRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindScheduledEventRepository(impl: ScheduledEventRepositoryImpl): ScheduledEventRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindListShareRepository(impl: ListShareRepositoryImpl): ListShareRepository

    // Не репозиторий, но живет рядом: это единственный Binds-модуль
    // проекта, и заводить ради одной связки еще один — шум.
    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    // Два каталога фото — один интерфейс PhotoStorage, различение
    // квалификаторами (семантика вложений разная, см. saver'ы).
    @Binds
    @Singleton
    @DocumentPhotos
    abstract fun bindDocumentPhotoStorage(impl: DocumentPhotoSaver): PhotoStorage

    @Binds
    @Singleton
    @NotePhotos
    abstract fun bindNotePhotoStorage(impl: NotePhotoSaver): PhotoStorage
}

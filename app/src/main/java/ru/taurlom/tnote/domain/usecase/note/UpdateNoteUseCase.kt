package ru.taurlom.tnote.domain.usecase.note

import ru.taurlom.tnote.di.NotePhotos
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import javax.inject.Inject

class UpdateNoteUseCase @Inject constructor(private val repository: NoteRepository, @NotePhotos private val photoStorage: PhotoStorage) {
    /**
     * Тот же безопасный порядок, что у документов после фикса гонок:
     * новые файлы — до записи, удаление старых — только после коммита базы
     * и только без оставшихся ссылок. Поля заголовка/текста берёт редактор,
     * остальное (position, createdAt, фото из параллельной правки) — свежий
     * снапшот базы.
     */
    suspend operator fun invoke(note: Note, newPhotoUris: List<String> = emptyList(), removedPhotoPaths: List<String> = emptyList()) {
        val newPaths = photoStorage.savePhotos(newPhotoUris)
        val fresh = repository.getByIdOnce(note.id) ?: note
        val updatedPaths = fresh.photoPaths
            .filterNot { it in removedPhotoPaths }
            .plus(newPaths)
        repository.update(
            fresh.copy(
                title = note.title,
                content = note.content,
                photoPaths = updatedPaths,
            ),
        )
        photoStorage.deletePhotos(removedPhotoPaths.filterNot { it in updatedPaths })
    }
}

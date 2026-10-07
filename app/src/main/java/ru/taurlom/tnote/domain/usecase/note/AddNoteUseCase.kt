package ru.taurlom.tnote.domain.usecase.note

import ru.taurlom.tnote.di.NotePhotos
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import java.time.Clock
import javax.inject.Inject

class AddNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    @NotePhotos private val photoStorage: PhotoStorage,
    private val clock: Clock,
) {
    /**
     * Позиция — из базы (max + 1), а не из снапшота UI; файлы фото ложатся
     * до записи: база ссылается только на существующее.
     */
    suspend operator fun invoke(note: Note, newPhotoUris: List<String> = emptyList()): Long {
        val savedPaths = photoStorage.savePhotos(newPhotoUris)
        return repository.insert(
            note.copy(
                createdAt = clock.millis(),
                position = repository.getMaxPosition() + 1,
                photoPaths = note.photoPaths + savedPaths,
            ),
        )
    }
}

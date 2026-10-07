package ru.taurlom.tnote.domain.usecase.note

import android.net.Uri
import ru.taurlom.tnote.data.local.NotePhotoSaver
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import java.time.Clock
import javax.inject.Inject

class AddNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val photoSaver: NotePhotoSaver,
    private val clock: Clock,
) {
    /**
     * Позиция — из базы (max + 1), а не из снапшота UI; файлы фото ложатся
     * до записи: база ссылается только на существующее.
     */
    suspend operator fun invoke(note: Note, newPhotoUris: List<Uri> = emptyList()): Long {
        val savedPaths = photoSaver.savePhotos(newPhotoUris)
        return repository.insert(
            note.copy(
                createdAt = clock.millis(),
                position = repository.getMaxPosition() + 1,
                photoPaths = note.photoPaths + savedPaths,
            ),
        )
    }
}

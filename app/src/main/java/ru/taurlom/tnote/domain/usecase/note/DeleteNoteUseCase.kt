package ru.taurlom.tnote.domain.usecase.note

import ru.taurlom.tnote.di.NotePhotos
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(private val repository: NoteRepository, @NotePhotos private val photoStorage: PhotoStorage) {
    /** Сначала строка базы (fk-каскад снимет ссылки), затем файлы с диска. */
    suspend operator fun invoke(note: Note) {
        repository.delete(note)
        photoStorage.deletePhotos(note.photoPaths)
    }
}

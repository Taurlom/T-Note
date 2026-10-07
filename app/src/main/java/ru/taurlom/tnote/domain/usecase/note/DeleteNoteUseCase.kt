package ru.taurlom.tnote.domain.usecase.note

import ru.taurlom.tnote.data.local.NotePhotoSaver
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val photoSaver: NotePhotoSaver
) {
    /** Сначала строка (фото-записи уходят каскадом), потом файлы на диске. */
    suspend operator fun invoke(note: Note) {
        repository.delete(note)
        photoSaver.deletePhotos(note.photoPaths)
    }
}

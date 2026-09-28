package com.example.timemanager.domain.usecase.note

import com.example.timemanager.data.local.NotePhotoSaver
import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.repository.NoteRepository
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

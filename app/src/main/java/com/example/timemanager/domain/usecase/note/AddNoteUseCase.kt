package com.example.timemanager.domain.usecase.note

import android.net.Uri
import com.example.timemanager.data.local.NotePhotoSaver
import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.repository.NoteRepository
import javax.inject.Inject

class AddNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val photoSaver: NotePhotoSaver
) {
    /**
     * Позиция — из базы (max + 1), а не из снапшота UI; файлы фото ложатся
     * до записи: база ссылается только на существующее.
     */
    suspend operator fun invoke(note: Note, newPhotoUris: List<Uri> = emptyList()): Long {
        val savedPaths = photoSaver.savePhotos(newPhotoUris)
        return repository.insert(
            note.copy(
                position = repository.getMaxPosition() + 1,
                photoPaths = note.photoPaths + savedPaths
            )
        )
    }
}

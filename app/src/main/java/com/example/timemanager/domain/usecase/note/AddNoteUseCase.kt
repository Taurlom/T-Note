package com.example.timemanager.domain.usecase.note

import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.repository.NoteRepository
import javax.inject.Inject

class AddNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    /** Позиция — из базы (max + 1), а не из снапшота UI: два добавления
     *  до переизлучения Flow не должны получить один порядок. */
    suspend operator fun invoke(note: Note): Long =
        repository.insert(note.copy(position = repository.getMaxPosition() + 1))
}

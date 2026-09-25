package com.example.timemanager.domain.usecase.note

import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.repository.NoteRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(note: Note) = repository.delete(note)
}

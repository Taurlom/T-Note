package com.example.timemanager.domain.usecase.note

import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.repository.NoteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetNoteByIdUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    operator fun invoke(id: Long): Flow<Note?> = repository.getById(id)
}

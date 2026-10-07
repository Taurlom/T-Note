package ru.taurlom.tnote.domain.usecase.note

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import javax.inject.Inject

class GetNoteByIdUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(id: Long): Flow<Note?> = repository.getById(id)
}

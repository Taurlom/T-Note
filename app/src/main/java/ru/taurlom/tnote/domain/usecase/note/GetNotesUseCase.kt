package ru.taurlom.tnote.domain.usecase.note

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import javax.inject.Inject

class GetNotesUseCase @Inject constructor(private val repository: NoteRepository) {
    operator fun invoke(): Flow<List<Note>> = repository.getAll()
}

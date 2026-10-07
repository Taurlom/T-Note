package ru.taurlom.tnote.domain.usecase.note

import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import javax.inject.Inject

/** Новый порядок всего списка после перетаскивания. */
class ReorderNotesUseCase @Inject constructor(private val repository: NoteRepository) {
    suspend operator fun invoke(notes: List<Note>) = repository.updatePositions(notes)
}

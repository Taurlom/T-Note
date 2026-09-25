package com.example.timemanager.domain.usecase.note

import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.repository.NoteRepository
import javax.inject.Inject

/** Новый порядок всего списка после перетаскивания. */
class ReorderNotesUseCase @Inject constructor(
    private val repository: NoteRepository
) {
    suspend operator fun invoke(notes: List<Note>) = repository.updatePositions(notes)
}

package com.example.timemanager.presentation.screens.notes

import com.example.timemanager.domain.model.Note

sealed class NotesEvent {
    data class OnDeleteNote(val note: Note) : NotesEvent()

    /** Новый порядок всего списка после перетаскивания. */
    data class OnReorderNotes(val notes: List<Note>) : NotesEvent()
}

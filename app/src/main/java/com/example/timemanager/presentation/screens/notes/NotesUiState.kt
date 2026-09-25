package com.example.timemanager.presentation.screens.notes

import com.example.timemanager.domain.model.Note

data class NotesUiState(
    val notes: List<Note> = emptyList()
)

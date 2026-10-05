package com.example.timemanager.presentation.screens.notes

import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.model.SharedText

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    /** Текст из системного «Поделиться», ждёт подтверждения в диалоге. */
    val incomingSharedText: SharedText? = null,
    /** Одноразовый результат сохранения — тост показывает AppNavigation. */
    val saveFeedback: NotesFeedback? = null
)

/** Результат сохранения шэра в заметки; аналог ShareFeedback категорий. */
sealed interface NotesFeedback {
    data class Saved(val noteTitle: String) : NotesFeedback
    data object Failed : NotesFeedback
}

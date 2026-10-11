package ru.taurlom.tnote.presentation.screens.notes

import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.model.SharedNote
import ru.taurlom.tnote.domain.model.SharedText

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    /** Текст из системного «Поделиться», ждёт подтверждения в диалоге. */
    val incomingSharedText: SharedText? = null,
    /** Заметка из `.tnote`-файла, ждёт подтверждения в диалоге. */
    val incomingSharedNote: SharedNote? = null,
    /** Одноразовый результат сохранения — тост показывает AppNavigation. */
    val saveFeedback: NotesFeedback? = null,
)

/** Результат сохранения шэра в заметки; аналог ShareFeedback категорий. */
sealed interface NotesFeedback {
    data class Saved(val noteTitle: String) : NotesFeedback
    data object Failed : NotesFeedback

    /** Пришедший файл не оказался заметкой T-Note (битый/чужой). */
    data object ImportFailed : NotesFeedback
}

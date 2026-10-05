package com.example.timemanager.presentation.screens.notes

import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.model.SharedText

sealed class NotesEvent {
    data class OnDeleteNote(val note: Note) : NotesEvent()

    /** Новый порядок всего списка после перетаскивания. */
    data class OnReorderNotes(val notes: List<Note>) : NotesEvent()

    /** Текст из системного «Поделиться»: показать диалог сохранения. */
    data class OnSharedTextReceived(val shared: SharedText) : NotesEvent()

    /** Подтверждение в диалоге: создать заметку из пришедшего текста. */
    data object OnConfirmSaveSharedText : NotesEvent()

    /** Отмена диалога (крестик/тап мимо): ничего не создаётся. */
    data object OnDismissSaveSharedText : NotesEvent()

    /** Тост показан — гасим одноразовый фидбек. */
    data object OnSharedTextFeedbackShown : NotesEvent()
}

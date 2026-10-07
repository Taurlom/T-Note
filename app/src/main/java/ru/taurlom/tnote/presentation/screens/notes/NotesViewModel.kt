package ru.taurlom.tnote.presentation.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.usecase.note.AddNoteUseCase
import ru.taurlom.tnote.domain.usecase.note.DeleteNoteUseCase
import ru.taurlom.tnote.domain.usecase.note.GetNotesUseCase
import ru.taurlom.tnote.domain.usecase.note.ReorderNotesUseCase
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val getNotesUseCase: GetNotesUseCase,
    private val addNoteUseCase: AddNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val reorderNotesUseCase: ReorderNotesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        getNotesUseCase()
            .onEach { notes -> _uiState.update { it.copy(notes = notes) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: NotesEvent) {
        when (event) {
            is NotesEvent.OnDeleteNote -> viewModelScope.launch {
                deleteNoteUseCase(event.note)
            }
            is NotesEvent.OnReorderNotes -> viewModelScope.launch {
                reorderNotesUseCase(
                    event.notes.mapIndexed { index, note -> note.copy(position = index) },
                )
            }
            is NotesEvent.OnSharedTextReceived -> _uiState.update {
                it.copy(incomingSharedText = event.shared)
            }
            is NotesEvent.OnConfirmSaveSharedText -> viewModelScope.launch {
                // Зеркало импорта .tnote: пришло — держим в состоянии, факт
                // сохранения — одноразовым фидбеком для тоста.
                val shared = _uiState.value.incomingSharedText ?: return@launch
                val title = shared.draftTitle()
                runCatching {
                    addNoteUseCase(Note(title = title, content = shared.text, createdAt = 0L))
                }.onSuccess {
                    _uiState.update {
                        it.copy(
                            incomingSharedText = null,
                            saveFeedback = NotesFeedback.Saved(title),
                        )
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            incomingSharedText = null,
                            saveFeedback = NotesFeedback.Failed,
                        )
                    }
                }
            }
            is NotesEvent.OnDismissSaveSharedText -> _uiState.update {
                it.copy(incomingSharedText = null)
            }
            is NotesEvent.OnSharedTextFeedbackShown -> _uiState.update {
                it.copy(saveFeedback = null)
            }
        }
    }
}

package com.example.timemanager.presentation.screens.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.usecase.note.AddNoteUseCase
import com.example.timemanager.domain.usecase.note.DeleteNoteUseCase
import com.example.timemanager.domain.usecase.note.GetNoteByIdUseCase
import com.example.timemanager.domain.usecase.note.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteDetailUiState(
    val note: Note? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false
)

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getNoteByIdUseCase: GetNoteByIdUseCase,
    private val addNoteUseCase: AddNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase
) : ViewModel() {

    private val noteId: Long = checkNotNull(savedStateHandle["noteId"])

    private val _uiState = MutableStateFlow(
        NoteDetailUiState(
            // Новая заметка сразу в редакторе; существующая — в просмотре.
            isLoading = noteId != NEW_NOTE_ID,
            isEditing = noteId == NEW_NOTE_ID
        )
    )
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    val isNew: Boolean get() = noteId == NEW_NOTE_ID

    init {
        if (noteId != NEW_NOTE_ID) {
            getNoteByIdUseCase(noteId)
                .onEach { note ->
                    _uiState.update { it.copy(note = note, isLoading = false) }
                }
                .launchIn(viewModelScope)
        }
    }

    fun startEditing() = _uiState.update { it.copy(isEditing = true) }

    fun stopEditing() = _uiState.update { it.copy(isEditing = false) }

    /** Сохранение: пустой заголовок не пишем, [onSaved] — только после записи. */
    fun save(title: String, content: String, onSaved: () -> Unit = {}) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = _uiState.value.note
            if (current == null) {
                addNoteUseCase(Note(title = trimmed, content = content))
            } else {
                updateNoteUseCase(current.copy(title = trimmed, content = content))
            }
            _uiState.update { it.copy(isEditing = false) }
            onSaved()
        }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        val note = _uiState.value.note ?: return
        viewModelScope.launch {
            deleteNoteUseCase(note)
            onDeleted()
        }
    }
}

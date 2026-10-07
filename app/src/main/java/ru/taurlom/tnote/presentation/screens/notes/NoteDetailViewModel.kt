package ru.taurlom.tnote.presentation.screens.notes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.usecase.note.AddNoteUseCase
import ru.taurlom.tnote.domain.usecase.note.DeleteNoteUseCase
import ru.taurlom.tnote.domain.usecase.note.GetNoteByIdUseCase
import ru.taurlom.tnote.domain.usecase.note.UpdateNoteUseCase
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

    /**
     * Сохранение: пустой заголовок не пишем, [onSaved] — только после записи.
     * Новые URI фото копирует и связывает с заметкой use-case; удалённые
     * пути передаются ему же — файлы снимает после коммита базы.
     */
    fun save(
        title: String,
        content: String,
        newPhotoUris: List<Uri> = emptyList(),
        removedPhotoPaths: List<String> = emptyList(),
        onSaved: () -> Unit = {}
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = _uiState.value.note
            if (current == null) {
                addNoteUseCase(Note(title = trimmed, content = content, createdAt = 0L), newPhotoUris)
            } else {
                updateNoteUseCase(
                    current.copy(title = trimmed, content = content),
                    newPhotoUris = newPhotoUris,
                    removedPhotoPaths = removedPhotoPaths
                )
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

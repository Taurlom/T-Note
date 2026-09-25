package com.example.timemanager.presentation.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.usecase.note.DeleteNoteUseCase
import com.example.timemanager.domain.usecase.note.GetNotesUseCase
import com.example.timemanager.domain.usecase.note.ReorderNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val getNotesUseCase: GetNotesUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val reorderNotesUseCase: ReorderNotesUseCase
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
                    event.notes.mapIndexed { index, note -> note.copy(position = index) }
                )
            }
        }
    }
}

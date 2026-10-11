package ru.taurlom.tnote.presentation.screens.notes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteShareRepository
import ru.taurlom.tnote.domain.usecase.note.AddNoteUseCase
import ru.taurlom.tnote.domain.usecase.note.DeleteNoteUseCase
import ru.taurlom.tnote.domain.usecase.note.GetNotesUseCase
import ru.taurlom.tnote.domain.usecase.note.ReorderNotesUseCase
import ru.taurlom.tnote.presentation.error.launchCatching
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val getNotesUseCase: GetNotesUseCase,
    private val addNoteUseCase: AddNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val reorderNotesUseCase: ReorderNotesUseCase,
    private val noteShareRepository: NoteShareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        getNotesUseCase()
            .onEach { notes -> _uiState.update { it.copy(notes = notes) } }
            .launchIn(viewModelScope)
    }

    /**
     * Быстрый ответ на «это файл заметки или списка?» — по первым байтам.
     * AppNavigation спрашивает перед тем, как решить, какой ViewModel
     * отдать пришедший `.tnote`.
     */
    suspend fun isNoteShareFile(uri: Uri): Boolean = noteShareRepository.isNoteFile(uri)

    fun onEvent(event: NotesEvent) {
        when (event) {
            is NotesEvent.OnDeleteNote -> launchCatching {
                deleteNoteUseCase(event.note)
            }

            is NotesEvent.OnReorderNotes -> launchCatching {
                reorderNotesUseCase(
                    event.notes.mapIndexed { index, note -> note.copy(position = index) },
                )
            }

            is NotesEvent.OnSharedTextReceived -> _uiState.update {
                it.copy(incomingSharedText = event.shared)
            }

            is NotesEvent.OnConfirmSaveSharedText -> launchCatching {
                // Зеркало импорта .tnote: пришло — держим в состоянии, факт
                // сохранения — одноразовым фидбеком для тоста.
                val shared = _uiState.value.incomingSharedText ?: return@launchCatching
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

            // Зеркало импорта списка из .tnote у категорий: прочитали —
            // держим в состоянии, факт сохранения — одноразовым фидбеком.
            is NotesEvent.OnReadSharedNote -> launchCatching {
                val shared = runCatching {
                    noteShareRepository.importFromFile(event.uri)
                }.getOrNull()
                _uiState.update {
                    it.copy(
                        incomingSharedNote = shared,
                        saveFeedback =
                        if (shared == null) NotesFeedback.ImportFailed else null,
                    )
                }
            }

            is NotesEvent.OnConfirmImportSharedNote -> launchCatching {
                val shared = _uiState.value.incomingSharedNote ?: return@launchCatching
                runCatching {
                    // Фото приходят временными file:// URI из cacheDir:
                    // use-case копирует их в хранилище и переписывает
                    // ссылки в тексте, как content:// из редактора.
                    addNoteUseCase(
                        Note(
                            title = shared.title,
                            content = shared.content,
                            createdAt = 0L,
                            tags = shared.tags,
                        ),
                        shared.photoUris,
                    )
                }.onSuccess {
                    _uiState.update {
                        it.copy(
                            incomingSharedNote = null,
                            saveFeedback = NotesFeedback.Saved(shared.title),
                        )
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(
                            incomingSharedNote = null,
                            saveFeedback = NotesFeedback.Failed,
                        )
                    }
                }
            }

            is NotesEvent.OnDismissImportSharedNote -> _uiState.update {
                it.copy(incomingSharedNote = null)
            }
        }
    }
}

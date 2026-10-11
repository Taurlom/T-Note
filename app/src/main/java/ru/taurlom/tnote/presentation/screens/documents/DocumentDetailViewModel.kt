package ru.taurlom.tnote.presentation.screens.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.usecase.document.DeleteDocumentUseCase
import ru.taurlom.tnote.domain.usecase.document.GetDocumentByIdUseCase
import ru.taurlom.tnote.domain.usecase.document.UpdateDocumentUseCase
import ru.taurlom.tnote.presentation.error.launchCatching
import javax.inject.Inject

data class DocumentDetailUiState(
    val document: Document? = null,
    val isLoading: Boolean = true,
    // Флаг выполняющегося удаления: пока true, повторный вызов игнорируется
    // (защита от двойного тапа по «Удалить» до рекомпозиции диалога).
    val isDeleting: Boolean = false,
)

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDocumentByIdUseCase: GetDocumentByIdUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val updateDocumentUseCase: UpdateDocumentUseCase,
) : ViewModel() {

    private val documentId: Long = checkNotNull(savedStateHandle["documentId"])

    private val _uiState = MutableStateFlow(DocumentDetailUiState(isLoading = true))
    val uiState: StateFlow<DocumentDetailUiState> = _uiState.asStateFlow()

    init {
        loadDocument()
    }

    private fun loadDocument() {
        getDocumentByIdUseCase(documentId)
            .onEach { document ->
                _uiState.update { it.copy(document = document, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun deleteDocument(onDeleted: () -> Unit) {
        val document = _uiState.value.document ?: return
        // Диалог закрывается синхронно, но очень быстрый двойной тап может
        // пройти до рекомпозиции — второй вызов отсекается флагом, иначе
        // onDeleted() (popBackStack) выполнится дважды.
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        launchCatching {
            try {
                deleteDocumentUseCase(document)
            } finally {
                // При отказе флаг снимаем — иначе повторное удаление было
                // бы заблокировано. onDeleted() в этом случае не зовём:
                // пользователь остаётся на экране и может повторить.
                _uiState.update { it.copy(isDeleting = false) }
            }
            onDeleted()
        }
    }

    fun updatePhotoPath(oldPath: String, newPath: String) {
        launchCatching {
            // Свежее чтение из базы внутри use-case: снапшот uiState мог
            // отстать и перезаписать список фото без новых путей.
            updateDocumentUseCase.renamePhotoPath(documentId, oldPath, newPath)
        }
    }
}

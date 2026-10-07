package ru.taurlom.tnote.presentation.screens.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.usecase.document.DeleteDocumentUseCase
import ru.taurlom.tnote.domain.usecase.document.GetDocumentByIdUseCase
import ru.taurlom.tnote.domain.usecase.document.UpdateDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentDetailUiState(
    val document: Document? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDocumentByIdUseCase: GetDocumentByIdUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val updateDocumentUseCase: UpdateDocumentUseCase
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
        viewModelScope.launch {
            deleteDocumentUseCase(document)
            onDeleted()
        }
    }
    
    fun updatePhotoPath(oldPath: String, newPath: String) {
        viewModelScope.launch {
            // Свежее чтение из базы внутри use-case: снапшот uiState мог
            // отстать и перезаписать список фото без новых путей.
            updateDocumentUseCase.renamePhotoPath(documentId, oldPath, newPath)
        }
    }
}

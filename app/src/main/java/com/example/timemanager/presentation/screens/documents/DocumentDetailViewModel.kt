package com.example.timemanager.presentation.screens.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.usecase.document.DeleteDocumentUseCase
import com.example.timemanager.domain.usecase.document.GetDocumentByIdUseCase
import com.example.timemanager.domain.usecase.document.UpdateDocumentUseCase
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
        val document = _uiState.value.document ?: return
        val updatedPhotos = document.photoPaths.map { 
            if (it == oldPath) newPath 
            else it 
        }
        val updatedDocument = document.copy(photoPaths = updatedPhotos)
        viewModelScope.launch {
            // oldPath передаём как removedPhotoPaths — UseCase удалит старый файл с диска
            updateDocumentUseCase(updatedDocument, removedPhotoPaths = listOf(oldPath))
        }
    }
}

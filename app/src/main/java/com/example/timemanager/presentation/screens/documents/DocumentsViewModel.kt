package com.example.timemanager.presentation.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.usecase.document.AddDocumentUseCase
import com.example.timemanager.domain.usecase.document.DeleteDocumentUseCase
import com.example.timemanager.domain.usecase.document.GetDocumentsUseCase
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

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val getDocumentsUseCase: GetDocumentsUseCase,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val updateDocumentUseCase: UpdateDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        getDocumentsUseCase()
            .onEach { documents ->
                _uiState.update { it.copy(documents = documents) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: DocumentsEvent) {
        when (event) {
            is DocumentsEvent.OnAddDocument -> {
                viewModelScope.launch {
                    addDocumentUseCase(
                        document = event.document,
                        photoUris = event.photoUris
                    )
                }
            }
            is DocumentsEvent.OnEditDocument -> {
                viewModelScope.launch {
                    updateDocumentUseCase(
                        document = event.document,
                        newPhotoUris = event.newPhotoUris,
                        removedPhotoPaths = event.removedPhotoPaths
                    )
                }
            }
            is DocumentsEvent.OnDeleteDocument -> {
                viewModelScope.launch {
                    deleteDocumentUseCase(event.document)
                }
            }
        }
    }
}

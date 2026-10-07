package ru.taurlom.tnote.presentation.screens.documents

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
import kotlinx.coroutines.launch
import ru.taurlom.tnote.domain.usecase.document.AddDocumentUseCase
import ru.taurlom.tnote.domain.usecase.document.DeleteDocumentUseCase
import ru.taurlom.tnote.domain.usecase.document.GetDocumentsUseCase
import ru.taurlom.tnote.domain.usecase.document.ReorderDocumentsUseCase
import ru.taurlom.tnote.domain.usecase.document.UpdateDocumentUseCase
import javax.inject.Inject

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val getDocumentsUseCase: GetDocumentsUseCase,
    private val addDocumentUseCase: AddDocumentUseCase,
    private val updateDocumentUseCase: UpdateDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val reorderDocumentsUseCase: ReorderDocumentsUseCase,
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
                    // createdAt, позицию (по базе, не по снапшоту UI) и пути
                    // новых фото заполнит AddDocumentUseCase.
                    addDocumentUseCase(
                        document = event.document,
                        // Domain работает с URI строками, не зная android.net.Uri.
                        photoUris = event.photoUris.map(Uri::toString),
                    )
                }
            }
            is DocumentsEvent.OnEditDocument -> {
                viewModelScope.launch {
                    updateDocumentUseCase(
                        document = event.document,
                        newPhotoUris = event.newPhotoUris.map(Uri::toString),
                        removedPhotoPaths = event.removedPhotoPaths,
                    )
                }
            }
            is DocumentsEvent.OnDeleteDocument -> {
                viewModelScope.launch {
                    deleteDocumentUseCase(event.document)
                }
            }
            is DocumentsEvent.OnReorderDocuments -> {
                viewModelScope.launch {
                    reorderDocumentsUseCase(
                        event.documents.mapIndexed { index, document ->
                            document.copy(position = index)
                        },
                    )
                }
            }
        }
    }
}

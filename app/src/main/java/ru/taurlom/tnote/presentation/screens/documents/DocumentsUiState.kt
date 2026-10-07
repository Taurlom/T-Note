package ru.taurlom.tnote.presentation.screens.documents

import ru.taurlom.tnote.domain.model.Document

data class DocumentsUiState(val documents: List<Document> = emptyList())

package com.example.timemanager.presentation.screens.documents

import com.example.timemanager.domain.model.Document

data class DocumentsUiState(
    val documents: List<Document> = emptyList()
)

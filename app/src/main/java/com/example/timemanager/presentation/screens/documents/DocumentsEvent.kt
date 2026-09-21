package com.example.timemanager.presentation.screens.documents

import android.net.Uri
import com.example.timemanager.domain.model.Document

sealed class DocumentsEvent {
    data class OnAddDocument(
        val document: Document,
        val photoUris: List<Uri>
    ) : DocumentsEvent()

    data class OnEditDocument(
        val document: Document,
        val newPhotoUris: List<Uri>,
        val removedPhotoPaths: List<String>
    ) : DocumentsEvent()

    data class OnDeleteDocument(val document: Document) : DocumentsEvent()

    /** Новый порядок всего списка после перетаскивания. */
    data class OnReorderDocuments(val documents: List<Document>) : DocumentsEvent()
}

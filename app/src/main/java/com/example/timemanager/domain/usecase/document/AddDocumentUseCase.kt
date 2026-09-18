package com.example.timemanager.domain.usecase.document

import android.net.Uri
import com.example.timemanager.data.local.DocumentPhotoSaver
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.repository.DocumentRepository
import javax.inject.Inject

class AddDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val photoSaver: DocumentPhotoSaver
) {
    suspend operator fun invoke(document: Document, photoUris: List<Uri> = emptyList()): Long {
        val savedPaths = photoSaver.savePhotos(photoUris)
        return repository.insert(document.copy(photoPaths = document.photoPaths + savedPaths))
    }
}

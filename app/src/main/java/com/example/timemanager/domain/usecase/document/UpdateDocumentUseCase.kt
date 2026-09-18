package com.example.timemanager.domain.usecase.document

import android.net.Uri
import com.example.timemanager.data.local.DocumentPhotoSaver
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.repository.DocumentRepository
import javax.inject.Inject

class UpdateDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val photoSaver: DocumentPhotoSaver
) {
    suspend operator fun invoke(
        document: Document,
        newPhotoUris: List<Uri> = emptyList(),
        removedPhotoPaths: List<String> = emptyList()
    ) {
        val newPaths = photoSaver.savePhotos(newPhotoUris)
        photoSaver.deletePhotos(removedPhotoPaths)

        val updatedPaths = document.photoPaths
            .filter { it !in removedPhotoPaths }
            .plus(newPaths)

        repository.update(document.copy(photoPaths = updatedPaths))
    }
}

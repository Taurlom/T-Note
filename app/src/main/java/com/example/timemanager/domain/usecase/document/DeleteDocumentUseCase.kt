package com.example.timemanager.domain.usecase.document

import com.example.timemanager.data.local.DocumentPhotoSaver
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.repository.DocumentRepository
import javax.inject.Inject

class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val photoSaver: DocumentPhotoSaver
) {
    suspend operator fun invoke(document: Document) {
        photoSaver.deletePhotos(document.photoPaths)
        repository.delete(document)
    }
}

package ru.taurlom.tnote.domain.usecase.document

import ru.taurlom.tnote.di.DocumentPhotos
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import javax.inject.Inject

class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    @DocumentPhotos private val photoStorage: PhotoStorage,
) {
    suspend operator fun invoke(document: Document) {
        photoStorage.deletePhotos(document.photoPaths)
        repository.delete(document)
    }
}

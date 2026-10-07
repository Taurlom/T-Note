package ru.taurlom.tnote.domain.usecase.document

import ru.taurlom.tnote.data.local.DocumentPhotoSaver
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
import javax.inject.Inject

class DeleteDocumentUseCase @Inject constructor(private val repository: DocumentRepository, private val photoSaver: DocumentPhotoSaver) {
    suspend operator fun invoke(document: Document) {
        photoSaver.deletePhotos(document.photoPaths)
        repository.delete(document)
    }
}

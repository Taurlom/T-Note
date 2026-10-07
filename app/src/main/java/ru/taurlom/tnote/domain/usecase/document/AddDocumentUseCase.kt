package ru.taurlom.tnote.domain.usecase.document

import android.net.Uri
import ru.taurlom.tnote.data.local.DocumentPhotoSaver
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
import java.time.Clock
import javax.inject.Inject

class AddDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val photoSaver: DocumentPhotoSaver,
    private val clock: Clock,
) {
    suspend operator fun invoke(document: Document, photoUris: List<Uri> = emptyList()): Long {
        val savedPaths = photoSaver.savePhotos(photoUris)
        return repository.insert(
            document.copy(
                createdAt = clock.millis(),
                photoPaths = document.photoPaths + savedPaths,
            ),
        )
    }
}

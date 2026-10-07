package ru.taurlom.tnote.domain.usecase.document

import ru.taurlom.tnote.di.DocumentPhotos
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import java.time.Clock
import javax.inject.Inject

class AddDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    @DocumentPhotos private val photoStorage: PhotoStorage,
    private val clock: Clock,
) {
    /**
     * Позиция — из базы (max + 1), а не из снапшота UI; файлы фото ложатся
     * до записи: база ссылается только на существующее.
     */
    suspend operator fun invoke(document: Document, photoUris: List<String> = emptyList()): Long {
        val savedPaths = photoStorage.savePhotos(photoUris)
        return repository.insert(
            document.copy(
                createdAt = clock.millis(),
                position = repository.getMaxPosition() + 1,
                photoPaths = document.photoPaths + savedPaths,
            ),
        )
    }
}

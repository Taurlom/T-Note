package ru.taurlom.tnote.domain.usecase.document

import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
import javax.inject.Inject

/** Сохраняет новый порядок документов после перетаскивания в списке. */
class ReorderDocumentsUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend operator fun invoke(documents: List<Document>) = repository.updatePositions(documents)
}

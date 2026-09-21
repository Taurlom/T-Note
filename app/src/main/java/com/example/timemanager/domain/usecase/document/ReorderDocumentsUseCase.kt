package com.example.timemanager.domain.usecase.document

import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.repository.DocumentRepository
import javax.inject.Inject

/** Сохраняет новый порядок документов после перетаскивания в списке. */
class ReorderDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    suspend operator fun invoke(documents: List<Document>) =
        repository.updatePositions(documents)
}

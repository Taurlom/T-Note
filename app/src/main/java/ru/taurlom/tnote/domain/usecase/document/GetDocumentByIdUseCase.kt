package ru.taurlom.tnote.domain.usecase.document

import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDocumentByIdUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    operator fun invoke(id: Long): Flow<Document?> = repository.getById(id)
}

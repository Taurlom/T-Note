package ru.taurlom.tnote.domain.usecase.document

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
import javax.inject.Inject

class GetDocumentsUseCase @Inject constructor(private val repository: DocumentRepository) {
    operator fun invoke(): Flow<List<Document>> = repository.getAll()
}

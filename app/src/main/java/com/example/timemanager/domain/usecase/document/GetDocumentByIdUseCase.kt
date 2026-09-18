package com.example.timemanager.domain.usecase.document

import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDocumentByIdUseCase @Inject constructor(
    private val repository: DocumentRepository
) {
    operator fun invoke(id: Long): Flow<Document?> = repository.getById(id)
}

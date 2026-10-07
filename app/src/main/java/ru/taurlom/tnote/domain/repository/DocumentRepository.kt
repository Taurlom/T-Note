package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Document

interface DocumentRepository {

    fun getAll(): Flow<List<Document>>
    fun getById(id: Long): Flow<Document?>
    suspend fun getMaxPosition(): Int
    suspend fun insert(document: Document): Long
    suspend fun update(document: Document)
    suspend fun updatePositions(documents: List<Document>)
    suspend fun delete(document: Document)
}

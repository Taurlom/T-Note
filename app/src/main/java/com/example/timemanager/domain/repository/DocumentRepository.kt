package com.example.timemanager.domain.repository

import com.example.timemanager.domain.model.Document
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {

    fun getAll(): Flow<List<Document>>
    fun getById(id: Long): Flow<Document?>
    suspend fun getMaxPosition(): Int
    suspend fun insert(document: Document): Long
    suspend fun update(document: Document)
    suspend fun updatePositions(documents: List<Document>)
    suspend fun delete(document: Document)
    suspend fun deleteAll()
}

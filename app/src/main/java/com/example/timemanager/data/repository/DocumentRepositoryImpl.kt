package com.example.timemanager.data.repository

import com.example.timemanager.data.local.DocumentDao
import com.example.timemanager.data.mapper.toDomain
import com.example.timemanager.data.mapper.toEntity
import com.example.timemanager.data.mapper.toPhotoEntities
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao
) : DocumentRepository {

    override fun getAll(): Flow<List<Document>> =
        dao.getAll().map { list ->
            list.map { it.document.toDomain(it.photos) }
        }

    override fun getById(id: Long): Flow<Document?> =
        dao.getById(id).map { it?.document?.toDomain(it.photos) }

    override suspend fun getMaxPosition(): Int = dao.getMaxPosition()

    override suspend fun insert(document: Document): Long =
        dao.insertDocumentWithPhotos(
            document = document.toEntity(),
            photos = document.toPhotoEntities()
        )

    override suspend fun update(document: Document) =
        dao.updateDocumentWithPhotos(
            document = document.toEntity(),
            photos = document.toPhotoEntities()
        )

    override suspend fun updatePositions(documents: List<Document>) {
        dao.updatePositions(documents.map { it.toEntity() })
    }

    override suspend fun delete(document: Document) =
        dao.delete(document.toEntity())

    override suspend fun deleteAll() =
        dao.deleteAll()
}

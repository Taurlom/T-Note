package ru.taurlom.tnote.data.repository

import ru.taurlom.tnote.data.local.DocumentDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.data.mapper.toPhotoEntities
import ru.taurlom.tnote.domain.model.Document
import ru.taurlom.tnote.domain.repository.DocumentRepository
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

package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.DocumentEntity
import ru.taurlom.tnote.data.local.entity.DocumentPhotoEntity
import ru.taurlom.tnote.domain.model.Document

fun DocumentEntity.toDomain(photos: List<DocumentPhotoEntity>): Document = Document(
    id = id,
    title = title,
    description = description,
    photoPaths = photos.sortedBy { it.orderIndex }.map { it.photoPath },
    createdAt = createdAt,
    position = position,
)

fun Document.toEntity(): DocumentEntity = DocumentEntity(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    position = position,
)

fun Document.toPhotoEntities(startOrderIndex: Int = 0): List<DocumentPhotoEntity> = photoPaths.mapIndexed { index, path ->
    DocumentPhotoEntity(
        documentId = id,
        photoPath = path,
        orderIndex = startOrderIndex + index,
    )
}

package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.DocumentEntity
import com.example.timemanager.data.local.entity.DocumentPhotoEntity
import com.example.timemanager.domain.model.Document

fun DocumentEntity.toDomain(photos: List<DocumentPhotoEntity>): Document = Document(
    id = id,
    title = title,
    description = description,
    photoPaths = photos.sortedBy { it.orderIndex }.map { it.photoPath },
    createdAt = createdAt
)

fun Document.toEntity(): DocumentEntity = DocumentEntity(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt
)

fun Document.toPhotoEntities(startOrderIndex: Int = 0): List<DocumentPhotoEntity> =
    photoPaths.mapIndexed { index, path ->
        DocumentPhotoEntity(
            documentId = id,
            photoPath = path,
            orderIndex = startOrderIndex + index
        )
    }

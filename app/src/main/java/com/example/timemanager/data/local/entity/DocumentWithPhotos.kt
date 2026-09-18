package com.example.timemanager.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class DocumentWithPhotos(
    @Embedded
    val document: DocumentEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val photos: List<DocumentPhotoEntity>
)

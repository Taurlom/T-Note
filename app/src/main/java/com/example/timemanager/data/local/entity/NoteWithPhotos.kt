package com.example.timemanager.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class NoteWithPhotos(
    @Embedded
    val note: NoteEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val photos: List<NotePhotoEntity>
)

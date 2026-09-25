package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.NoteEntity
import com.example.timemanager.domain.model.Note

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    position = position
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    position = position
)

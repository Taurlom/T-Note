package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.CalendarNoteEntity
import ru.taurlom.tnote.domain.model.CalendarNote

fun CalendarNoteEntity.toDomain(): CalendarNote = CalendarNote(
    date = eventDate,
    text = text,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

fun CalendarNote.toEntity(): CalendarNoteEntity = CalendarNoteEntity(
    eventDate = date,
    text = text,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

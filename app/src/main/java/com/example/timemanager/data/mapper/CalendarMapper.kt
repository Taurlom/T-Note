package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.CalendarNoteEntity
import com.example.timemanager.domain.model.CalendarNote

fun CalendarNoteEntity.toDomain(): CalendarNote = CalendarNote(
    date = eventDate,
    text = text
)

fun CalendarNote.toEntity(): CalendarNoteEntity = CalendarNoteEntity(
    eventDate = date,
    text = text
)

package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.CalendarNoteEntity
import com.example.timemanager.data.local.entity.CalendarTaskEntity
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.CalendarTask

fun CalendarNoteEntity.toDomain(): CalendarNote = CalendarNote(
    date = eventDate,
    text = text
)

fun CalendarNote.toEntity(): CalendarNoteEntity = CalendarNoteEntity(
    eventDate = date,
    text = text
)

fun CalendarTaskEntity.toDomain(): CalendarTask = CalendarTask(
    id = id,
    date = eventDate,
    text = text,
    isCompleted = isCompleted,
    position = position
)

fun CalendarTask.toEntity(): CalendarTaskEntity = CalendarTaskEntity(
    id = id,
    eventDate = date,
    text = text,
    isCompleted = isCompleted,
    position = position
)

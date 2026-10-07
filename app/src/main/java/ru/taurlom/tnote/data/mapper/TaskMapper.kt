package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.TaskEntity
import ru.taurlom.tnote.domain.model.Task

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    categoryId = categoryId,
    createdAt = createdAt,
    position = position,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    categoryId = categoryId,
    createdAt = createdAt,
    position = position,
)

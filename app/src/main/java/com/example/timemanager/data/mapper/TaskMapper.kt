package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.TaskEntity
import com.example.timemanager.domain.model.Task

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    categoryId = categoryId,
    createdAt = createdAt
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    categoryId = categoryId,
    createdAt = createdAt
)

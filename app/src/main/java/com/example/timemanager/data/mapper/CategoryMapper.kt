package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.CategoryEntity
import com.example.timemanager.domain.model.Category

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    color = color,
    position = position
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    color = color,
    position = position
)

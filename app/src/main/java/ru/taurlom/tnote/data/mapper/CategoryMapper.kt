package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.CategoryEntity
import ru.taurlom.tnote.domain.model.Category

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    color = color,
    position = position,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    color = color,
    position = position,
)

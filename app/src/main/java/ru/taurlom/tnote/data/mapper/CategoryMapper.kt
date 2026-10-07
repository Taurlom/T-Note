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
    // archived не входит в доменную модель: флаг живут в отдельных запросах
    // DAO (archive/restore). update()/insert() всегда пишут archived = false —
    // это безопасно, пока редактирование и сортировка доступны только для
    // активных списков (архивный список через update() не прогоняют).
)

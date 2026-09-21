package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.ScheduledEventEntity
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType

fun ScheduledEventEntity.toDomain(): ScheduledEvent = ScheduledEvent(
    id = id,
    date = eventDate,
    title = title,
    time = time,
    type = runCatching { ScheduledEventType.valueOf(type) }
        .getOrDefault(ScheduledEventType.REGULAR),
    alarmEnabled = alarmEnabled,
    position = position
)

fun ScheduledEvent.toEntity(): ScheduledEventEntity = ScheduledEventEntity(
    id = id,
    eventDate = date,
    title = title,
    time = time,
    type = type.name,
    alarmEnabled = alarmEnabled,
    position = position
)

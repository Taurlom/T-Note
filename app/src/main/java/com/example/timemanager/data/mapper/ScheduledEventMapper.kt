package com.example.timemanager.data.mapper

import com.example.timemanager.data.local.entity.ScheduledEventEntity
import com.example.timemanager.domain.model.EventIcon
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType

fun ScheduledEventEntity.toDomain(): ScheduledEvent = ScheduledEvent(
    id = id,
    date = eventDate,
    title = title,
    type = runCatching { ScheduledEventType.valueOf(type) }
        .getOrDefault(ScheduledEventType.REGULAR),
    icon = runCatching { EventIcon.valueOf(icon) }.getOrDefault(EventIcon.NOTE),
    colorArgb = colorArgb,
    repeatIntervalDays = repeatIntervalDays,
    repeatDays = repeatDays,
    hidePastOccurrences = hidePast,
    position = position
)

fun ScheduledEvent.toEntity(): ScheduledEventEntity = ScheduledEventEntity(
    id = id,
    eventDate = date,
    title = title,
    type = type.name,
    icon = icon.name,
    colorArgb = colorArgb,
    repeatIntervalDays = repeatIntervalDays,
    repeatDays = repeatDays,
    hidePast = hidePastOccurrences,
    position = position
)

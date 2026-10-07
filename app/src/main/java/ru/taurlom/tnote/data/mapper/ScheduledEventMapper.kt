package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType

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

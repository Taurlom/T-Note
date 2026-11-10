package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.RepeatUnit
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType

fun ScheduledEventEntity.toDomain(): ScheduledEvent = ScheduledEvent(
    id = id,
    uid = uid,
    date = eventDate,
    title = title,
    type = runCatching { ScheduledEventType.valueOf(type) }
        .getOrDefault(ScheduledEventType.REGULAR),
    icon = runCatching { EventIcon.valueOf(icon) }.getOrDefault(EventIcon.NOTE),
    colorArgb = colorArgb,
    repeatUnit = repeatUnit?.let { unit -> runCatching { RepeatUnit.valueOf(unit) }.getOrNull() },
    repeatInterval = repeatInterval,
    repeatWeekdays = ScheduledEvent.weekdaysFromCode(repeatWeekdays),
    repeatUntil = repeatUntil,
    exdates = ScheduledEvent.exdatesFromCode(exdates),
    hidePastOccurrences = hidePast,
    position = position,
    caldavAccountId = caldavAccountId,
    remoteImported = remoteImported,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

fun ScheduledEvent.toEntity(): ScheduledEventEntity = ScheduledEventEntity(
    id = id,
    uid = uid,
    eventDate = date,
    title = title,
    type = type.name,
    icon = icon.name,
    colorArgb = colorArgb,
    repeatUnit = repeatUnit?.name,
    repeatInterval = repeatInterval,
    repeatWeekdays = ScheduledEvent.weekdaysToCode(repeatWeekdays),
    repeatUntil = repeatUntil,
    exdates = ScheduledEvent.exdatesToCode(exdates),
    hidePast = hidePastOccurrences,
    position = position,
    caldavAccountId = caldavAccountId,
    remoteImported = remoteImported,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
)

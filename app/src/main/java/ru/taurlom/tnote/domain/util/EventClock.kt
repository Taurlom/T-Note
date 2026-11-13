package ru.taurlom.tnote.domain.util

import java.util.Locale

/**
 * Часы события в 24-часовом виде: минуты от полуночи → «10:00».
 *
 * Формат не зависит от локали устройства — так же, как и весь ICS-контракт:
 * время события показывается одинаково в интерфейсе, в уведомлении и на
 * сервере. Часы лежат в [ru.taurlom.tnote.domain.model.ScheduledEvent.startMinutes]
 * и `endMinutes`.
 */
fun formatEventClock(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

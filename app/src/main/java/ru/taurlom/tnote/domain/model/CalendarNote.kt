package ru.taurlom.tnote.domain.model

/**
 * Заметка дня — отрывной стикер на календарной дате.
 *
 * [date] — ключ "yyyy-MM-dd", одна заметка на день. Метки времени —
 * основа будущего разрешения конфликтов (последняя правка побеждает);
 * 0 — унаследовано от версий, где их не было.
 */
data class CalendarNote(
    val date: String,
    val text: String,
    /** Момент создания (epoch millis); 0 — унаследовано от старых версий. */
    val createdAt: Long = 0,
    /** Момент последней правки (epoch millis); 0 — унаследовано от старых версий. */
    val modifiedAt: Long = 0,
)

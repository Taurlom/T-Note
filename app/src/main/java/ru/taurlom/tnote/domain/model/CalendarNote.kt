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

    /**
     * CalDAV-подписка, в чью коллекцию заметка уезжает (этап 2); null —
     * локальная заметка: подписок нет или их несколько (выбор не
     * угадывается — как у событий, см. CaldavAttribution).
     */
    val caldavAccountId: Long? = null,
    /** Локальная правка ещё не уехала на сервер — очередь PUT'а. */
    val dirty: Boolean = false,
    /** Последний известный ETag серверного объекта — предусловие If-Match. */
    val etag: String? = null,
    /** Href серверного объекта; null — заметка ни разу не гуляла по серверу. */
    val serverHref: String? = null,
)

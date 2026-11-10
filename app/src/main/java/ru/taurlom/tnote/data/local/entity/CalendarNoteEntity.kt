package ru.taurlom.tnote.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_notes")
data class CalendarNoteEntity(
    /**
     * ISO-дата «YYYY-MM-DD» — первичный ключ: одна заметка на день.
     * Авто-increment id здесь был ловушкой: каждое сохранение вставляло
     * новый ряд вместо обновления (дубли копились вечно).
     */
    @PrimaryKey
    val eventDate: String,
    val text: String,

    /** Момент создания (epoch millis); 0 — унаследовано от старых версий. */
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = 0,
    /** Момент последней правки (epoch millis); 0 — унаследовано от старых версий. */
    @ColumnInfo(defaultValue = "0")
    val modifiedAt: Long = 0,

    /**
     * CalDAV-подписка, в чью коллекцию заметка уезжает (этап 2); null —
     * локальная заметка. Приписку делает репозиторий при сохранении:
     * единственная активная подписка забирает заметку себе, как и события.
     */
    val caldavAccountId: Long? = null,

    /**
     * Очередь двустороннего обмена: локальная правка ещё не уехала на
     * сервер (PUT «✍️ Заметка» с текстом в DESCRIPTION). Ставится
     * репозиторием при сохранении, снимается движком после успешного PUT.
     */
    @ColumnInfo(defaultValue = "0")
    val dirty: Boolean = false,

    /** Последний известный ETag серверного объекта — предусловие If-Match. */
    val etag: String? = null,

    /**
     * Href серверного объекта из вычитки; null — заметка ни разу не
     * гуляла по серверу.
     */
    val serverHref: String? = null,
)

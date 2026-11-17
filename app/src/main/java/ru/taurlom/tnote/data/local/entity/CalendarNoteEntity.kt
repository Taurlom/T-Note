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
     * Фактический UID события-носителя на сервере. Приложение публикует
     * носитель с детерминированным uid от даты (noteUidFor), но объект
     * мог быть создан в веб-форме или чужим клиентом — тогда его uid
     * другой, и вычитка приносит его дословно. Без этого поля tombstone
     * и подавление вычитки сверялись бы с производным uid и не находили
     * очередь — удалённая заметка воскресала бы до подтверждения DELETE.
     * null — неизвестен (свои строки до первой вычитки, локальные);
     * очереди в этом случае служит производный uid.
     */
    val remoteUid: String? = null,

    /**
     * Очередь двустороннего обмена: локальная правка ещё не уехала на
     * сервер (PUT «✍️ Заметка» с текстом в DESCRIPTION). Ставится
     * репозиторием при сохранении, снимается движком после успешного PUT.
     */
    @ColumnInfo(defaultValue = "0")
    val dirty: Boolean = false,

    /**
     * Счётчик локальных мутаций — опознаватель отправленной версии строки:
     * PUT подтверждается CAS'ом по нему, и правка, вписанная в сетевой ход,
     * остаётся в очереди (см. ScheduledEventEntity.localRevision).
     */
    @ColumnInfo(defaultValue = "0")
    val localRevision: Long = 0,

    /** Последний известный ETag серверного объекта — предусловие If-Match. */
    val etag: String? = null,

    /**
     * Href серверного объекта из вычитки; null — заметка ни разу не
     * гуляла по серверу.
     */
    val serverHref: String? = null,
)

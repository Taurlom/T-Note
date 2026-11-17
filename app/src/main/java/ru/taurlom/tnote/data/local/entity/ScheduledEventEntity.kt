package ru.taurlom.tnote.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_events",
    indices = [
        Index("eventDate"),
        Index(value = ["caldavAccountId", "uid"], unique = true),
        Index("caldavAccountId"),
    ],
)
data class ScheduledEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * CalDAV UID события (см. ScheduledEvent.uid): идентифицирует объект
     * внутри одной подписки — (caldavAccountId, uid) составной уникальный
     * ключ. Два сервера могут хранить разные объекты под одним UID
     * (перенос события между календарями сохраняет UID), и оба обязаны
     * переживать в базе отдельными строками.
     */
    @ColumnInfo(defaultValue = "")
    val uid: String,
    val eventDate: String,
    val title: String,
    val type: String,
    @ColumnInfo(defaultValue = "NOTE")
    val icon: String,
    @ColumnInfo(defaultValue = "0")
    val colorArgb: Long,

    /** Частота повторения (DAYS/WEEKS/MONTHS/YEARS); null — не повторяется. */
    val repeatUnit: String?,
    /** «Каждые N единиц» частоты (см. ScheduledEvent.repeatInterval). */
    @ColumnInfo(defaultValue = "1")
    val repeatInterval: Int,
    /** Дни недели для WEEKS — коды RFC "MO,WE,FR" (null — день якоря). */
    val repeatWeekdays: String?,
    /** Последний день повторения включительно ("yyyy-MM-dd"); null — бессрочно. */
    val repeatUntil: String?,
    /** Отменённые вхождения — ISO-даты через запятую (EXDATE); null — без отмен. */
    val exdates: String? = null,
    @ColumnInfo(defaultValue = "0")
    val hidePast: Boolean,
    /**
     * Время начала события — минуты от полуночи (см.
     * ScheduledEvent.startMinutes). null — дневная метка, времени нет.
     */
    val startMinutes: Int? = null,
    /** Время завершения события — минуты от полуночи; null — конец не задан. */
    val endMinutes: Int? = null,
    /**
     * Дата конца события ("yyyy-MM-dd"); null — событие в один день.
     * Только чужие (Google, Яндекс) события длиннее суток: у них конец
     * приходит на другой день (см. ScheduledEvent.endDate).
     */
    val endDate: String? = null,
    val position: Int = 0,

    /** CalDAV-аккаунт-источник (null — локальное событие, см. ScheduledEvent). */
    val caldavAccountId: Long? = null,

    /**
     * «UID появился в базе вычиткой с сервера» (импорт чужого события;
     * перепись и правки флаг не меняют, после отправки собственного
     * события в подписку он остаётся ложным). Только по нему рисуется
     * бейдж источника (Яндекс/Google): приписка подписке у caldavAccountId
     * бывает и у событий, созданных в приложении, так что по ней
     * происхождение не видно.
     */
    @ColumnInfo(defaultValue = "0")
    val remoteImported: Boolean = false,

    /**
     * Очередь двустороннего обмена (этап 2): локальная правка ещё не уехала
     * на сервер (PUT). Ставится репозиторием при правке, снимается движком
     * после успешного PUT.
     */
    @ColumnInfo(defaultValue = "0")
    val dirty: Boolean = false,
    /**
     * Счётчик локальных мутаций строки — опознаватель «той версии, что
     * отправляли». PUT подтверждается CAS'ом по нему ([dirty] снимается лишь
     * когда строка с момента вычитки в очередь не менялась): правка,
     * вписанная в сетевой ход, не должна быть помечена отправленной. По
     * [modifiedAt] такой проверки нет — две правки в одну миллисекунду
     * неотличимы, а счётчик растёт на каждом локальном изменении.
     */
    @ColumnInfo(defaultValue = "0")
    val localRevision: Long = 0,
    /**
     * Последний известный ETag объекта — предусловие If-Match при PUT
     * («не затри чужое»). Хранится как вернул сервер, вместе с кавычками.
     */
    val etag: String? = null,
    /**
     * Href объекта на сервере из REPORT («/calendars/…/<uid>.ics»): PUT/DELETE
     * бьют по нему, а не выводят имя файла из UID — сервер мог назвать
     * объект по-своему. null — событие ни разу не гуляло по серверу.
     */
    val serverHref: String? = null,

    /** Момент создания (epoch millis); null — унаследовано от старых версий. */
    val createdAt: Long? = null,
    /** Момент последней правки (epoch millis); null — унаследовано от старых версий. */
    val modifiedAt: Long? = null,
)

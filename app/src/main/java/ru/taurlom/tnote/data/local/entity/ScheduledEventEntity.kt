package ru.taurlom.tnote.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_events",
    indices = [Index("eventDate"), Index("uid", unique = true), Index("caldavAccountId")],
)
data class ScheduledEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** CalDAV UID события (см. ScheduledEvent.uid): уникален по архитектуре. */
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
    val position: Int = 0,

    /** CalDAV-аккаунт-источник (null — локальное событие, см. ScheduledEvent). */
    val caldavAccountId: Long? = null,

    /** Момент создания (epoch millis); null — унаследовано от старых версий. */
    val createdAt: Long? = null,
    /** Момент последней правки (epoch millis); null — унаследовано от старых версий. */
    val modifiedAt: Long? = null,
)

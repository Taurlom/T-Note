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
    val repeatIntervalDays: Int?,
    @ColumnInfo(defaultValue = "0")
    val repeatDays: Int,
    @ColumnInfo(defaultValue = "0")
    val hidePast: Boolean,
    val position: Int = 0,

    /** CalDAV-аккаунт-источник (null — локальное событие, см. ScheduledEvent). */
    val caldavAccountId: Long? = null,
)

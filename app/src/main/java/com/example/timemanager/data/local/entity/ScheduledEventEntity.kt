package com.example.timemanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_events",
    indices = [Index("eventDate")]
)
data class ScheduledEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventDate: String,
    val title: String,
    val type: String,
    @ColumnInfo(defaultValue = "NOTE")
    val icon: String,
    @ColumnInfo(defaultValue = "0")
    val colorArgb: Long,
    val repeatPeriod: String?,
    val repeatIntervalDays: Int?,
    @ColumnInfo(defaultValue = "0")
    val repeatDays: Int,
    @ColumnInfo(defaultValue = "0")
    val hidePast: Boolean,
    val position: Int = 0
)

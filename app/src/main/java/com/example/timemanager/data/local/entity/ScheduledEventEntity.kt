package com.example.timemanager.data.local.entity

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
    val time: String?,
    val type: String,
    val alarmEnabled: Boolean,
    val position: Int = 0
)

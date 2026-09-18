package com.example.timemanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_tasks",
    indices = [Index("eventDate")]
)
data class CalendarTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventDate: String,
    val text: String,
    val isCompleted: Boolean = false,
    val position: Int = 0
)

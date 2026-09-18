package com.example.timemanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_notes")
data class CalendarNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventDate: String,
    val text: String
)

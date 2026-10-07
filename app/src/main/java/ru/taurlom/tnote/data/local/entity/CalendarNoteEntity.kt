package ru.taurlom.tnote.data.local.entity

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
)

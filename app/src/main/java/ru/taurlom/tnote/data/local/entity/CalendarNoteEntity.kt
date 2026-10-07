package ru.taurlom.tnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_notes")
data class CalendarNoteEntity(
    /**
     * ISO-дата ЂYYYY-MM-DDї Ч первичный ключ: одна заметка на день.
     * јвто-increment id здесь был ловушкой: каждое сохранение вставл€ло
     * новый р€д вместо обновлени€ (дубли копились вечно).
     */
    @PrimaryKey
    val eventDate: String,
    val text: String
)

package ru.taurlom.tnote.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Long,
    val position: Int = 0,
    /**
     * Архивный список: скрыт из основного перечня, задачи внутри целы.
     * DEFAULT обязателен — миграция 16→17 добавляет колонку через ALTER TABLE,
     * и схема без defaultValue не пройдёт валидацию Room.
     */
    @ColumnInfo(defaultValue = "0")
    val archived: Boolean = false,
)

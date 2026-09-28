package com.example.timemanager.domain.model

/**
 * Заметка раздела «Заметки»: длинный текст с markdown-подмножеством
 * (заголовки, жирный, списки, ссылки). В отличие от документа, где главное —
 * фотография, здесь главное — текст.
 */
data class Note(
    val id: Long = 0,
    val title: String,
    /** Markdown-исходник: храним обычный текст с метками, не span-метаданные. */
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Порядок в списке (перетаскивание), как у документов. */
    val position: Int = 0,
    /** Прикреплённые фото (относительные пути в filesDir/note_photos). */
    val photoPaths: List<String> = emptyList()
)

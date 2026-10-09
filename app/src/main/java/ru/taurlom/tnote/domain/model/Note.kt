package ru.taurlom.tnote.domain.model

/**
 * Заметка раздела «Заметки»: длинный текст с markdown-форматированием
 * (заголовки, жирный, списки, ссылки). В отличие от документа, где главное —
 * фотография, здесь главное — текст.
 */
data class Note(
    val id: Long = 0,
    val title: String,
    /**
     * Markdown-исходник: храним обычный текст с метками, не span-метаданные.
     * Изображения — ссылки `![alt](path)` прямо в тексте: их позиция — часть
     * содержимого, а [photoPaths] — лишь реестр файлов для чистки.
     */
    val content: String = "",
    /** Время создания (мс UTC). Проставляется use-case'ом через [java.time.Clock]. */
    val createdAt: Long,
    /** Порядок в списке (перетаскивание), как у документов. */
    val position: Int = 0,
    /** Фото заметки (относительные пути в filesDir/note_photos) в порядке ссылок текста. */
    val photoPaths: List<String> = emptyList(),
)

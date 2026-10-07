package ru.taurlom.tnote.domain.model

data class Document(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val photoPaths: List<String> = emptyList(),
    /** Время создания (мс UTC). Проставляется use-case'ом через [java.time.Clock]. */
    val createdAt: Long,
    /** Порядок в списке (перетаскивание), как у задач и категорий. */
    val position: Int = 0,
)

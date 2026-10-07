package ru.taurlom.tnote.domain.model

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val categoryId: Long,
    /** Время создания (мс UTC). Проставляется use-case'ом через [java.time.Clock]. */
    val createdAt: Long,
    val position: Int = 0,
)

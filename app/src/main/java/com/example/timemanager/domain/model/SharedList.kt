package com.example.timemanager.domain.model

/**
 * Список, подготовленный для передачи между T-Note (файл `.tnote`).
 * Id осознанно нет: при импорте создаются свои.
 */
data class SharedList(
    val name: String,
    /** null — получатель возьмёт цвет по умолчанию. */
    val color: Long?,
    val tasks: List<SharedTask>
)

data class SharedTask(
    val title: String,
    val description: String = "",
    val completed: Boolean = false
)

/** Конвенции формата для интентов, манифеста и codec. */
object ListShareFormat {
    const val MIME_TYPE = "application/x-tnote-list"
    const val EXTENSION = ".tnote"
}

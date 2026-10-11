package ru.taurlom.tnote.domain.model

/**
 * Заметка, подготовленная для передачи между T-Note (файл `.tnote`).
 * Id/position/createdAt осознанно нет: при импорте заметка создаётся
 * новой записью, ничего у получателя не затирая.
 *
 * [photoUris] — временные file:// URI фотографий в cacheDir: импорт
 * передаёт их в AddNoteUseCase как есть, тот копирует файлы в постоянное
 * хранилище и переписывает ссылки в тексте (тот же путь, что у
 * content:// из редактора).
 */
data class SharedNote(
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val photoUris: List<String> = emptyList(),
)

/** Конвенции формата для интентов, манифеста и codec. */
object NoteShareFormat {
    const val MIME_TYPE = "application/x-tnote-note"
    const val EXTENSION = ".tnote"
}

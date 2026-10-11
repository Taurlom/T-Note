package ru.taurlom.tnote.domain.repository

import android.net.Uri
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.model.SharedNote

/**
 * Обмен одиночными заметками файлом `.tnote` между установками T-Note
 * (аналог [ListShareRepository] для списков задач).
 */
interface NoteShareRepository {

    /**
     * Быстрая проверка по первым байтам: файл-заметка или нет.
     * Полного чтения нет — магическое поле формата пишется первым.
     */
    suspend fun isNoteFile(uri: Uri): Boolean

    /** Пишет заметку (с фото) во временный файл шэринга; URI для ACTION_SEND. */
    suspend fun exportNote(note: Note): Uri

    /**
     * Читает чужой файл заметки. Фото раскладываются по временным файлам
     * cacheDir — дальше их забирает AddNoteUseCase как обычные URI.
     */
    suspend fun importFromFile(uri: Uri): SharedNote
}

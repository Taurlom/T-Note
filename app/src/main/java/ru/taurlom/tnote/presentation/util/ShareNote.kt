package ru.taurlom.tnote.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.model.NoteShareFormat
import ru.taurlom.tnote.domain.util.NoteImageRefs

/**
 * Поделиться заметкой текстом: заголовок + markdown-исходник.
 * Получателю не нужен T-Note — текст читается в любом мессенджере,
 * почте, SMS. Ссылки на фото вырезаются: локальные пути
 * `note_photos/…` получателю бесполезны и выглядят мусором.
 */
fun shareNote(context: Context, note: Note) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, note.title)
        putExtra(Intent.EXTRA_TEXT, buildNoteShareText(note))
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_note)),
    )
}

/**
 * Поделиться заметкой файлом `.tnote` — получатель с T-Note импортирует
 * её к себе с фото и тегами (открытием файла или «Поделиться → T-Note»).
 */
fun shareNoteFile(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = NoteShareFormat.MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_note)),
    )
}

/**
 * Текст заметки для отправки: заголовок, пустая строка, содержимое
 * без ссылок на изображения. Пустые абзацы от вырезанных картинок
 * схлопываются.
 */
fun buildNoteShareText(note: Note): String {
    val text = NoteImageRefs.tokenizeImageRefs(note.content, "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    val title = note.title.trim()
    return if (text.isEmpty()) title else "$title\n\n$text"
}

package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.presentation.theme.TNoteTheme
import ru.taurlom.tnote.presentation.util.Markdown
import ru.taurlom.tnote.presentation.util.noteImageModel

/**
 * Строка списка заметок: заголовок и превью текста без markdown-меток —
 * сами метки в списке только мешают, рендер полный — в деталях. С фото —
 * квадратная миниатюра первого изображения слева; удаление — только
 * внутри заметки (в списке двойной тап по малой цели только мешает).
 */
@Composable
fun NoteItem(note: Note, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (note.photoPaths.isNotEmpty()) {
            // Первое изображение в порядке ссылок текста — оно и есть обложка.
            // Вверху, а не по центру: выравнивается с заголовком карточки.
            AsyncImage(
                model = noteImageModel(LocalContext.current, note.photoPaths.first()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Фон до загрузки: миниатюра не «мигает» на тёмной теме.
                modifier = Modifier
                    .align(Alignment.Top)
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = Markdown.preview(note.content)
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.tags.isNotEmpty()) {
                // Одна строка со свайпом: бейджи не спорят с превью текста.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    note.tags.forEach { tag -> TagChip(tag) }
                }
            }
        }
    }
}

// ── Previews ──

private val previewNote = Note(
    id = 1,
    title = "Идеи для проекта",
    content = "Продумать архитектуру **модулей** и нарисовать схему.",
    createdAt = 0L,
    position = 0,
    tags = listOf("проект", "в работе"),
)

@Preview(showBackground = true, name = "С текстом")
@Composable
private fun NoteItemWithTextPreview() {
    TNoteTheme {
        NoteItem(note = previewNote, onClick = {})
    }
}

private val previewNoteWithPhoto = previewNote.copy(
    photoPaths = listOf("note_photos/cover.jpg"),
)

@Preview(showBackground = true, name = "С фото")
@Composable
private fun NoteItemWithPhotoPreview() {
    TNoteTheme {
        NoteItem(note = previewNoteWithPhoto, onClick = {})
    }
}

@Preview(showBackground = true, name = "Пустая")
@Composable
private fun NoteItemEmptyPreview() {
    TNoteTheme {
        NoteItem(
            note = previewNote.copy(title = "Пустая заметка", content = ""),
            onClick = {},
        )
    }
}

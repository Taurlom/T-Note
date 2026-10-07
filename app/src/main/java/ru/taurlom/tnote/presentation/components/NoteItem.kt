package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme
import ru.taurlom.tnote.presentation.util.Markdown

/**
 * Строка списка заметок: заголовок и превью текста без markdown-меток —
 * сами метки в списке только мешают, рендер полный — в деталях.
 */
@Composable
fun NoteItem(note: Note, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
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
            if (note.photoPaths.isNotEmpty()) {
                // Счётчик вложений, как у документов.
                Text(
                    text = "${note.photoPaths.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.delete),
                tint = AppTheme.colors.actionIcon,
            )
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
)

@Preview(showBackground = true, name = "С текстом")
@Composable
private fun NoteItemWithTextPreview() {
    TNoteTheme {
        NoteItem(note = previewNote, onClick = {}, onDelete = {})
    }
}

@Preview(showBackground = true, name = "Пустая")
@Composable
private fun NoteItemEmptyPreview() {
    TNoteTheme {
        NoteItem(
            note = previewNote.copy(title = "Пустая заметка", content = ""),
            onClick = {},
            onDelete = {},
        )
    }
}

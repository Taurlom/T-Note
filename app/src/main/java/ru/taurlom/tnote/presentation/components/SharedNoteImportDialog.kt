package ru.taurlom.tnote.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.SharedNote
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Подтверждение импорта заметки из `.tnote`-файла: что за заметка и
 * сколько в ней фото. Закрытие (крестик) — импорт отменяется, данные
 * не меняются.
 */
@Composable
fun SharedNoteImportDialog(shared: SharedNote, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.shared_note_import_title),
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = if (shared.photoUris.isEmpty()) {
                    stringResource(R.string.shared_note_import_text, shared.title)
                } else {
                    stringResource(
                        R.string.shared_note_import_text_photos,
                        shared.title,
                        shared.photoUris.size,
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.dialogContent,
            )
        },
        confirmButton = {
            AppTextButton(onClick = onConfirm, textRes = R.string.shared_note_add_action)
        },
    )
}

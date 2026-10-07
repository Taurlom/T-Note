package ru.taurlom.tnote.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.SharedText
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Подтверждение сохранения текста из системного «Поделиться»: превью
 * заголовка и первых строк. Закрытие (крестик) — отмена, данные не
 * создаются. Зеркало [SharedListImportDialog] для текстового шэра.
 */
@Composable
fun SharedTextSaveDialog(shared: SharedText, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.shared_text_import_title),
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(R.string.shared_text_import_text, shared.draftTitle()),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.dialogContent,
            )
            // Многострочный шэр: первые строки одной строкой, чтобы диалог
            // не распухал на весь экран.
            Text(
                text = shared.preview(),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.dialogContentMuted,
            )
        },
        confirmButton = {
            AppTextButton(onClick = onConfirm, textRes = R.string.shared_text_save_action)
        },
    )
}

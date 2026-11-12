package ru.taurlom.tnote.presentation.update

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.components.AppDialog
import ru.taurlom.tnote.presentation.components.AppTextButton
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Диалог «доступна новая версия». «Обновить» открывает страницу
 * приложения в RuStore; крестик — «Позже» (до следующего запуска
 * приложения), «Пропустить эту версию» — навсегда, до выхода следующей.
 */
@Composable
fun UpdateDialog(state: UpdateViewModel.DialogState, onUpdate: () -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.update_dialog_title),
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(R.string.update_dialog_text, state.latestVersionName),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.dialogContent,
            )
        },
        confirmButton = { AppTextButton(onClick = onUpdate, textRes = R.string.update_now) },
        dismissButton = { AppTextButton(onClick = onSkip, textRes = R.string.update_skip_version) },
    )
}

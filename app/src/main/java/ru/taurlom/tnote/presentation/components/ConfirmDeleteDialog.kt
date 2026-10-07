package ru.taurlom.tnote.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme
import androidx.compose.ui.tooling.preview.Preview
import ru.taurlom.tnote.presentation.theme.TNoteTheme

/** Стандартный диалог подтверждения удаления. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AppDialog(
        title = title,
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.dialogContent
            )
        },
        confirmButton = { AppTextButton(onClick = onConfirm, textRes = R.string.delete) }
    )
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun ConfirmDeleteDialogPreview() {
    TNoteTheme {
        ConfirmDeleteDialog(
            title = "Удалить задачу",
            text = "Вы уверены, что хотите удалить «Купить продукты»?",
            onDismiss = {},
            onConfirm = {}
        )
    }
}

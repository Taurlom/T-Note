package com.example.timemanager.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

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
        confirmButton = { AppTextButton(onClick = onConfirm, textRes = R.string.delete) },
        dismissButton = { AppCancelButton(onClick = onDismiss) }
    )
}

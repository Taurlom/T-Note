package com.example.timemanager.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.timemanager.R
import com.example.timemanager.domain.model.SharedList
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Подтверждение импорта списка из `.tnote`-файла: что за список и сколько
 * в нём пунктов. Закрытие (крестик) — импорт отменяется, данные не меняются.
 */
@Composable
fun SharedListImportDialog(
    shared: SharedList,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = stringResource(R.string.shared_list_import_title),
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(
                    R.string.shared_list_import_text,
                    shared.name,
                    shared.tasks.size
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.dialogContent
            )
            if (shared.tasks.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.shared_list_import_summary,
                        shared.tasks.count { !it.completed },
                        shared.tasks.count { it.completed }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.dialogContentMuted
                )
            }
        },
        confirmButton = {
            AppTextButton(onClick = onConfirm, textRes = R.string.shared_list_add_action)
        }
    )
}

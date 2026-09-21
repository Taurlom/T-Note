package com.example.timemanager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.domain.model.Category
import com.example.timemanager.presentation.theme.OnTertiary

@Composable
fun CategoryInputDialog(
    category: Category? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var selectedColor by remember { mutableLongStateOf(category?.color ?: defaultCategoryColor()) }

    AppDialog(
        title = stringResource(
            if (category == null) R.string.add_category else R.string.edit_category
        ),
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.category_name),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.choose_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = OnTertiary
                )
                ColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )
            }
        },
        confirmButton = {
            AppSaveButton(
                onClick = { onConfirm(name, selectedColor) },
                enabled = name.isNotBlank()
            )
        },
        dismissButton = { AppCancelButton(onClick = onDismiss) }
    )
}

@Composable
fun TaskInputDialog(
    titleInitial: String = "",
    descriptionInitial: String = "",
    dialogTitle: String,
    hasExistingTasks: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onNext: ((String, String) -> Unit)? = null
) {
    var title by remember { mutableStateOf(titleInitial) }
    var description by remember { mutableStateOf(descriptionInitial) }

    AppDialog(
        title = dialogTitle,
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.task_title),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            AppTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.task_description),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasExistingTasks && onNext != null) {
                    AppButton(
                        onClick = {
                            onNext(title, description)
                            title = ""
                            description = ""
                        },
                        enabled = title.isNotBlank()
                    ) {
                        Text(stringResource(R.string.next))
                    }
                }
                AppSaveButton(
                    onClick = { onConfirm(title, description) },
                    enabled = title.isNotBlank()
                )
            }
        },
        dismissButton = { AppCancelButton(onClick = onDismiss) }
    )
}

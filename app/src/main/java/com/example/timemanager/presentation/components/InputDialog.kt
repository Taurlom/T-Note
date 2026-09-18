package com.example.timemanager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.timemanager.presentation.theme.DialogBackground
import com.example.timemanager.presentation.theme.DialogButtonBackground
import com.example.timemanager.presentation.theme.OnPrimary
import com.example.timemanager.presentation.theme.OnTertiary

@Composable
fun CategoryInputDialog(
    category: Category? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var selectedColor by remember { mutableLongStateOf(category?.color ?: defaultCategoryColor()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        title = {
            Text(
                text = if (category == null) {
                    stringResource(R.string.add_category)
                } else {
                    stringResource(R.string.edit_category)
                },
                color = OnTertiary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnTertiary,
                        unfocusedTextColor = OnTertiary,
                        disabledTextColor = OnTertiary,
                        errorTextColor = OnTertiary,
                        cursorColor = OnTertiary,
                        focusedBorderColor = DialogButtonBackground,
                        unfocusedBorderColor = DialogButtonBackground.copy(alpha = 0.6f),
                        focusedLabelColor = DialogButtonBackground,
                        unfocusedLabelColor = DialogButtonBackground.copy(alpha = 0.6f)
                    )
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
            Button(
                onClick = { onConfirm(name, selectedColor) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DialogButtonBackground,
                    contentColor = OnPrimary,
                    disabledContainerColor = DialogButtonBackground.copy(alpha = 0.5f),
                    disabledContentColor = OnPrimary.copy(alpha = 0.5f)
                )
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DialogButtonBackground,
                    contentColor = OnPrimary
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(3.dp)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        title = { Text(dialogTitle, color = OnTertiary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnTertiary,
                        unfocusedTextColor = OnTertiary,
                        disabledTextColor = OnTertiary,
                        errorTextColor = OnTertiary,
                        cursorColor = OnTertiary,
                        focusedBorderColor = DialogButtonBackground,
                        unfocusedBorderColor = DialogButtonBackground.copy(alpha = 0.6f),
                        focusedLabelColor = DialogButtonBackground,
                        unfocusedLabelColor = DialogButtonBackground.copy(alpha = 0.6f)
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.task_description)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnTertiary,
                        unfocusedTextColor = OnTertiary,
                        disabledTextColor = OnTertiary,
                        errorTextColor = OnTertiary,
                        cursorColor = OnTertiary,
                        focusedBorderColor = DialogButtonBackground,
                        unfocusedBorderColor = DialogButtonBackground.copy(alpha = 0.6f),
                        focusedLabelColor = DialogButtonBackground,
                        unfocusedLabelColor = DialogButtonBackground.copy(alpha = 0.6f)
                    )
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasExistingTasks && onNext != null) {
                    Button(
                        onClick = {
                            onNext(title, description)
                            title = ""
                            description = ""
                        },
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(3.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DialogButtonBackground,
                            contentColor = OnPrimary,
                            disabledContainerColor = DialogButtonBackground.copy(alpha = 0.5f),
                            disabledContentColor = OnPrimary.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(stringResource(R.string.next))
                    }
                }
                Button(
                    onClick = { onConfirm(title, description) },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DialogButtonBackground,
                        contentColor = OnPrimary,
                        disabledContainerColor = DialogButtonBackground.copy(alpha = 0.5f),
                        disabledContentColor = OnPrimary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DialogButtonBackground,
                    contentColor = OnPrimary
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(3.dp)
    )
}

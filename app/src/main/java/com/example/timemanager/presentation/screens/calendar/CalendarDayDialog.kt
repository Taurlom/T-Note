package com.example.timemanager.presentation.screens.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.CalendarTask
import com.example.timemanager.presentation.theme.DialogBackground
import com.example.timemanager.presentation.theme.DialogButtonBackground
import com.example.timemanager.presentation.theme.OnPrimary
import com.example.timemanager.presentation.theme.OnSurfaceVariant
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.Secondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun CalendarDayDialog(
    date: CalendarDate,
    note: CalendarNote?,
    tasks: List<CalendarTask>,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDeleteDay: () -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (CalendarTask) -> Unit,
    onUpdateTaskText: (CalendarTask) -> Unit,
    onDeleteTask: (CalendarTask) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var noteText by remember(note) { mutableStateOf(note?.text.orEmpty()) }

    fun hideAndAction(action: () -> Unit) {
        scope.launch {
            try {
                sheetState.hide()
            } catch (_: Throwable) {
                // ignore
            }
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DialogBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = date.toDisplayName(),
                style = MaterialTheme.typography.titleLarge,
                color = OnTertiary
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text(stringResource(R.string.note_label)) },
                minLines = 4,
                maxLines = 6,
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { hideAndAction { onDeleteDay() } },
                    enabled = note != null,
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DialogButtonBackground,
                        contentColor = OnPrimary,
                        disabledContainerColor = DialogButtonBackground.copy(alpha = 0.5f),
                        disabledContentColor = OnPrimary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
                Button(
                    onClick = { hideAndAction { onSaveNote(noteText) } },
                    enabled = noteText.isNotBlank(),
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
        }
    }
}

@Suppress("unused")
@Composable
private fun CalendarTaskItem(
    task: CalendarTask,
    onToggle: () -> Unit,
    onUpdate: (String) -> Unit,
    onDelete: () -> Unit
) {
    var taskText by remember(task.id) { mutableStateOf(task.text) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = DialogButtonBackground,
                uncheckedColor = OnSurfaceVariant
            )
        )
        OutlinedTextField(
            value = taskText,
            onValueChange = { taskText = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OnTertiary,
                unfocusedTextColor = OnTertiary,
                cursorColor = OnTertiary,
                focusedBorderColor = DialogButtonBackground,
                unfocusedBorderColor = DialogButtonBackground.copy(alpha = 0.6f)
            )
        )
        IconButton(
            onClick = { onUpdate(taskText) }
        ) {
            Text("OK", color = Secondary)
        }
        IconButton(
            onClick = onDelete
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = OnSurfaceVariant
            )
        }
    }
}


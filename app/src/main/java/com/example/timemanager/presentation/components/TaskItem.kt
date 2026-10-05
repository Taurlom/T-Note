package com.example.timemanager.presentation.components

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.domain.model.Task
import com.example.timemanager.presentation.theme.AppTheme

@Composable
fun TaskItem(
    task: Task,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    // Отметка — главное микродействие списка: короткая вибрация
    // подтверждает его, даже когда палец уже ушёл с экрана. LongPress —
    // единственный общесистемный тип отклика в текущем Compose UI
    // (тонкие Confirm/SegmentTick появятся со стеком 1.8+). Обработчик
    // общий для обоих путей: тапа по чекбоксу и по строке.
    fun toggleWithHaptic() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggleCompletion()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = ::toggleWithHaptic)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppCheckbox(
            checked = task.isCompleted,
            onCheckedChange = { toggleWithHaptic() },
            onDarkBackground = true
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
            )
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.edit),
                    tint = AppTheme.colors.actionIcon
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete),
                    tint = AppTheme.colors.actionIcon
                )
            }
        }
    }
}

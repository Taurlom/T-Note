package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Диалог «Настройка разделов»: чекбоксы видимости и стрелки порядка.
 * Единственный источник истины — [visibleSections] из состояния (диалог
 * ничего не копит локально), каждое изменение применяется сразу: нижняя
 * панель живёт за диалогом и перестраивается на глазах.
 */
@Composable
fun SectionsDialog(visibleSections: List<String>, onDismiss: () -> Unit, onApply: (List<String>) -> Unit) {
    val shown = BottomNavItem.itemsFor(visibleSections)
        .filterNot { it == BottomNavItem.Settings }
    val shownIds = shown.map { it.id }
    val hidden = BottomNavItem.toggleable.filterNot { it.id in shownIds }

    AppDialog(
        title = stringResource(R.string.sections_button),
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEachIndexed { index, item ->
                    SectionRow(
                        item = item,
                        checked = true,
                        canMoveUp = index > 0,
                        canMoveDown = index < shown.lastIndex,
                        // Последний видимый раздел скрыть нельзя: бар без
                        // страниц остался бы только с «Настройками».
                        onToggle = {
                            if (shown.size > 1) onApply(shownIds - item.id)
                        },
                        onMoveUp = {
                            onApply(
                                shownIds.toMutableList().apply {
                                    add(index - 1, removeAt(index))
                                },
                            )
                        },
                        onMoveDown = {
                            onApply(
                                shownIds.toMutableList().apply {
                                    add(index + 1, removeAt(index))
                                },
                            )
                        },
                    )
                }
                hidden.forEach { item ->
                    SectionRow(
                        item = item,
                        checked = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        // Включение — в конец видимого списка.
                        onToggle = { onApply(shownIds + item.id) },
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss, textRes = R.string.done)
        },
    )
}

@Composable
private fun SectionRow(
    item: BottomNavItem,
    checked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppCheckbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            onDarkBackground = false,
        )
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            tint = AppTheme.colors.dialogContent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(item.contentDescriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.dialogContent,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_upward),
                    contentDescription = stringResource(R.string.section_move_up),
                    tint = AppTheme.colors.dialogContent,
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_downward),
                    contentDescription = stringResource(R.string.section_move_down),
                    tint = AppTheme.colors.dialogContent,
                )
            }
        }
    }
}

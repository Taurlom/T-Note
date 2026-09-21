package com.example.timemanager.presentation.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.EventIcon
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.presentation.components.AppButton
import com.example.timemanager.presentation.components.AppCancelButton
import com.example.timemanager.presentation.components.AppDialog
import com.example.timemanager.presentation.components.AppOutlinedButton
import com.example.timemanager.presentation.components.AppSaveButton
import com.example.timemanager.presentation.components.AppTextButton
import com.example.timemanager.presentation.components.AppTextField
import com.example.timemanager.presentation.theme.DialogContainer
import com.example.timemanager.presentation.theme.OnSurfaceVariant
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.PrimaryButtonContainer
import com.example.timemanager.presentation.theme.Secondary
import com.example.timemanager.presentation.theme.OnPrimary
import com.example.timemanager.presentation.theme.Primary
import java.time.LocalDate

/**
 * Диалог выбранного дня календаря: заметка и список событий дня.
 * Построен на компонентах дизайн-системы ([AppDialog], [AppTextField], кнопки).
 */
@Composable
fun CalendarDayDialog(
    date: CalendarDate,
    note: CalendarNote?,
    events: List<ScheduledEvent>,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDeleteDay: () -> Unit,
    onAddEvent: (ScheduledEvent) -> Unit,
    onUpdateEvent: (ScheduledEvent) -> Unit,
    onDeleteEvent: (ScheduledEvent) -> Unit
) {
    var noteText by remember(note) { mutableStateOf(note?.text.orEmpty()) }
    // null — редактор закрыт; ScheduledEvent с id == 0 — новый черновик.
    var editorTarget by remember { mutableStateOf<ScheduledEvent?>(null) }
    val dateKey = date.toIsoString()

    AppDialog(
        title = date.toDisplayName(),
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = stringResource(R.string.note_label),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            if (events.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.events_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = OnTertiary
                )
                events.forEach { event ->
                    ScheduledEventRow(
                        event = event,
                        onClick = { editorTarget = event },
                        onDelete = { onDeleteEvent(event) }
                    )
                }
            }

            AppOutlinedButton(
                onClick = { editorTarget = ScheduledEvent(date = dateKey, title = "") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.add_event),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        confirmButton = {
            AppButton(
                onClick = {
                    // Заметку сохраняем только если есть текст: пустая запись
                    // дня не нужна, а события уже сохранены через редактор.
                    if (noteText.isNotBlank()) onSaveNote(noteText)
                    onDismiss()
                },
                // Активна, есть что сохранить (заметка) или день с содержимым.
                enabled = noteText.isNotBlank() || note != null || events.isNotEmpty()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextButton(
                    onClick = {
                        onDeleteDay()
                        onDismiss()
                    },
                    textRes = R.string.delete,
                    enabled = note != null || events.isNotEmpty()
                )
                AppCancelButton(onClick = onDismiss)
            }
        }
    )

    editorTarget?.let { target ->
        ScheduledEventEditorDialog(
            original = target.takeIf { it.id != 0L },
            defaultDate = dateKey,
            onDismiss = { editorTarget = null },
            onSave = { saved ->
                if (target.id == 0L) onAddEvent(saved) else onUpdateEvent(saved)
                editorTarget = null
            },
            onDelete = {
                // Кнопка видна только для сохранённого события.
                target.takeIf { it.id != 0L }?.let { event -> onDeleteEvent(event) }
                editorTarget = null
            }
        )
    }
}

/** Строка события в диалоге дня. Прошедшие события приглушены и зачёркнуты. */
@Composable
private fun ScheduledEventRow(
    event: ScheduledEvent,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isPast = remember(event) { event.isPastOccurrence() }
    val titleColor = if (isPast) OnTertiary.copy(alpha = 0.45f) else OnTertiary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(
                if (event.type == ScheduledEventType.BIRTHDAY) {
                    R.drawable.ic_redeem
                } else {
                    event.icon.drawableRes
                }
            ),
            contentDescription = null,
            tint = eventIconColor(event.colorArgb, isPast, PrimaryButtonContainer),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            textDecoration = if (isPast) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.event_delete_description),
                tint = Primary
            )
        }
    }
}

/**
 * Редактор события: тип (обычное/день рождения) из выпадающего списка,
 * название, а для обычного события — выбор иконки, которая будет
 * отображаться под числом в календаре.
 * День рождения повторяется каждый год автоматически.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduledEventEditorDialog(
    original: ScheduledEvent?,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (ScheduledEvent) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var type by remember { mutableStateOf(original?.type ?: ScheduledEventType.REGULAR) }
    var icon by remember { mutableStateOf(original?.icon ?: EventIcon.NOTE) }
    var colorArgb by remember {
        mutableStateOf(original?.colorArgb ?: ScheduledEvent.DEFAULT_COLOR)
    }
    var expanded by remember { mutableStateOf(false) }

    AppDialog(
        title = stringResource(
            if (original == null) R.string.add_event else R.string.edit_event
        ),
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.event_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Тип события: выпадающий список «Обычное» / «День рождения».
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                AppTextField(
                    value = stringResource(type.labelRes),
                    onValueChange = { },
                    label = stringResource(R.string.event_type),
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = DialogContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ScheduledEventType.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(option.labelRes),
                                    color = OnTertiary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(option.iconRes),
                                    contentDescription = null,
                                    tint = OnTertiary
                                )
                            },
                            onClick = {
                                type = option
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Иконку и её цвет выбирает только обычное событие: день рождения
            // всегда отмечается подарком.
            if (type == ScheduledEventType.REGULAR) {
                Text(
                    text = stringResource(R.string.event_icon),
                    style = MaterialTheme.typography.labelLarge,
                    color = OnTertiary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventIcon.entries.forEach { option ->
                        EventIconOption(
                            icon = option,
                            selected = icon == option,
                            onClick = { icon = option }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.event_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = OnTertiary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Первый кружок — «по умолчанию»: тематический цвет календаря.
                    EventColorOption(
                        swatch = Secondary,
                        selected = colorArgb == ScheduledEvent.DEFAULT_COLOR,
                        label = stringResource(R.string.color_default),
                        onClick = { colorArgb = ScheduledEvent.DEFAULT_COLOR }
                    )
                    eventColorPresets.forEach { preset ->
                        EventColorOption(
                            swatch = Color(preset),
                            selected = colorArgb == preset,
                            label = null,
                            onClick = { colorArgb = preset }
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppSaveButton(
                onClick = {
                    onSave(
                        ScheduledEvent(
                            id = original?.id ?: 0L,
                            // Для дня рождения год в дате не меняется (см. репозиторий),
                            // обычное событие остаётся на открытой дате.
                            date = original?.date ?: defaultDate,
                            title = title,
                            type = type,
                            icon = icon,
                            colorArgb = colorArgb,
                            position = original?.position ?: 0
                        )
                    )
                },
                enabled = title.isNotBlank()
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (original != null) {
                    // Удаление только этого события, день остаётся.
                    AppTextButton(
                        onClick = onDelete,
                        textRes = R.string.delete
                    )
                }
                AppCancelButton(onClick = onDismiss)
            }
        }
    )
}

/** Кружок-переключатель цвета иконки события. */
@Composable
private fun EventColorOption(
    swatch: Color,
    selected: Boolean,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) OnTertiary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClickLabel = label) { onClick() }
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Кружок-переключатель иконки события с подписью под ним. */
@Composable
private fun EventIconOption(
    icon: EventIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) PrimaryButtonContainer
                    else OnTertiary.copy(alpha = 0.08f)
                )
        ) {
            Icon(
                painter = painterResource(icon.drawableRes),
                contentDescription = stringResource(icon.labelRes),
                tint = if (selected) OnPrimary else OnTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = stringResource(icon.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) OnTertiary else OnSurfaceVariant
        )
    }
}

/**
 * Произошло ли событие уже (для зачёркивания в списке).
 * Повторяющиеся дни рождения не «проходят» — они наступают каждый год.
 */
private fun ScheduledEvent.isPastOccurrence(today: LocalDate = LocalDate.now()): Boolean {
    if (type == ScheduledEventType.BIRTHDAY) return false
    val day = dateOrNull() ?: return false
    return day.isBefore(today)
}

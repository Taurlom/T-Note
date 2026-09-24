package com.example.timemanager.presentation.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.EventIcon
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.presentation.components.AppButton
import com.example.timemanager.presentation.components.AppCheckbox
import com.example.timemanager.presentation.components.AppDialog
import com.example.timemanager.presentation.components.AppDropdown
import com.example.timemanager.presentation.components.AppOutlinedButton
import com.example.timemanager.presentation.components.AppSaveButton
import com.example.timemanager.presentation.components.AppTextButton
import com.example.timemanager.presentation.components.AppTextField
import com.example.timemanager.presentation.theme.AppTheme
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
                    color = AppTheme.colors.dialogContent
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
            AppTextButton(
                onClick = {
                    onDeleteDay()
                    onDismiss()
                },
                textRes = R.string.delete,
                enabled = note != null || events.isNotEmpty()
            )
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
    val titleColor = if (isPast) AppTheme.colors.dialogContent.copy(alpha = 0.45f) else AppTheme.colors.dialogContent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(
                when (event.type) {
                    ScheduledEventType.BIRTHDAY -> R.drawable.ic_event_cake
                    ScheduledEventType.WEEKEND -> R.drawable.ic_weekend
                    else -> event.icon.drawableRes
                }
            ),
            contentDescription = null,
            tint = eventIconColor(event.colorArgb, isPast, AppTheme.colors.dialogContent),
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
                tint = AppTheme.colors.dialogContent
            )
        }
    }
}

/**
 * Редактор события: тип (обычное/день рождения/повторяющееся/выходной) из
 * выпадающего списка, название, а для обычных и повторяющихся — выбор иконки
 * и её цвета. «Выходной» дополнительных полей не имеет: он просто подсвечивает
 * день как выходной.
 * У повторяющегося события настраиваются интервал «раз в N дней»,
 * скрытие прошедших вхождений и длительность повторения.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var intervalText by remember {
        mutableStateOf(original?.repeatIntervalDays?.toString().orEmpty())
    }
    var hidePast by remember { mutableStateOf(original?.hidePastOccurrences ?: false) }
    var repeatDaysText by remember {
        mutableStateOf(original?.repeatDays?.takeIf { it > 0 }?.toString().orEmpty())
    }

    val intervalDays = intervalText.toIntOrNull() ?: 0
    // Повторяющему событию нужен интервал «раз в N дней».
    val repeatConfigured = type != ScheduledEventType.REPEATING || intervalDays > 0

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
                required = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Тип события: выпадающий список «Обычное» / «День рождения» /
            // «Повторяющееся».
            AppDropdown(
                label = stringResource(R.string.event_type),
                selectedLabel = stringResource(type.labelRes),
                options = ScheduledEventType.entries.toList(),
                optionText = { option ->
                    Text(
                        text = stringResource(option.labelRes),
                        color = AppTheme.colors.dialogContent
                    )
                },
                optionIcon = { option ->
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = null,
                        tint = AppTheme.colors.dialogContent
                    )
                },
                onSelect = { type = it }
            )

            if (type == ScheduledEventType.REPEATING) {
                AppTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                    label = stringResource(R.string.repeat_every_days),
                    singleLine = true,
                    // Без интервала повторяющее событие не существует.
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = repeatDaysText,
                    onValueChange = { repeatDaysText = it.filter(Char::isDigit).take(4) },
                    label = stringResource(R.string.repeat_days),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                // Флажок без 48-dp минимальной зоны касания Material — иначе
                // визуальный квадратик центрируется в ней и смещён вправо, не
                // сходясь с краём полей. В material3 1.3.x старый
                // LocalMinimumInteractiveComponentEnforcement компонентами уже
                // не читается, управляем через размер.
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppCheckbox(
                            checked = hidePast,
                            onCheckedChange = { hidePast = it },
                            onDarkBackground = false,
                            // Приглушённый цвет по умолчанию сливался бы с бежевым фоном.
                            uncheckedColor = AppTheme.colors.dialogContent
                        )
                        Text(
                            text = stringResource(R.string.repeat_hide_past),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.dialogContent
                        )
                    }
                }
            }

            // Иконку и её цвет выбирают обычные и повторяющиеся события:
            // день рождения отмечается подарком, «Выходной» — подсветкой дня.
            if (type == ScheduledEventType.REGULAR || type == ScheduledEventType.REPEATING) {
                Text(
                    text = stringResource(R.string.event_icon),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.dialogContent
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    color = AppTheme.colors.dialogContent
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Первый кружок — «по умолчанию»: цвет маркера события в сетке.
                    EventColorOption(
                        swatch = AppTheme.colors.calendarEventMarker,
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
                            // Для дня рождения и повторяющегося события год в
                            // дате не меняется (см. репозиторий), обычное
                            // событие остаётся на открытой дате.
                            date = original?.date ?: defaultDate,
                            title = title,
                            type = type,
                            icon = icon,
                            colorArgb = colorArgb,
                            repeatIntervalDays =
                                if (type == ScheduledEventType.REPEATING) {
                                    intervalDays.takeIf { it > 0 }
                                } else {
                                    null
                                },
                            repeatDays = if (type == ScheduledEventType.REPEATING) {
                                repeatDaysText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            } else {
                                0
                            },
                            hidePastOccurrences =
                                type == ScheduledEventType.REPEATING && hidePast,
                            position = original?.position ?: 0
                        )
                    )
                },
                enabled = title.isNotBlank() && repeatConfigured
            )
        },
        dismissButton = if (original != null) {
            // Удаление только этого события, день остаётся.
            { AppTextButton(onClick = onDelete, textRes = R.string.delete) }
        } else {
            null
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
                color = if (selected) AppTheme.colors.dialogContent else Color.Transparent,
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

/** Кружок-переключатель иконки события — в размер палитры цветов. */
@Composable
private fun EventIconOption(
    icon: EventIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (selected) colors.chipSelectedContainer
                else colors.chipContainer
            )
            .clickable(
                onClickLabel = stringResource(icon.labelRes),
                onClick = onClick
            )
    ) {
        Icon(
            painter = painterResource(icon.drawableRes),
            contentDescription = stringResource(icon.labelRes),
            tint = if (selected) colors.chipSelectedContent else colors.chipContent,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Произошло ли событие уже (для зачёркивания в списке).
 * Повторяющиеся дни рождения не «проходят» — они наступают каждый год.
 */
private fun ScheduledEvent.isPastOccurrence(today: LocalDate = LocalDate.now()): Boolean {
    // Дни рождения и пометки «Выходной» не «проходят»: они отмечают день.
    if (type == ScheduledEventType.BIRTHDAY || type == ScheduledEventType.WEEKEND) {
        return false
    }
    val day = dateOrNull() ?: return false
    return day.isBefore(today)
}

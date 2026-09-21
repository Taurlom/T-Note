package com.example.timemanager.presentation.screens.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.presentation.components.AppButton
import com.example.timemanager.presentation.components.AppCancelButton
import com.example.timemanager.presentation.components.AppDialog
import com.example.timemanager.presentation.components.AppFilterChip
import com.example.timemanager.presentation.components.AppOutlinedButton
import com.example.timemanager.presentation.components.AppSaveButton
import com.example.timemanager.presentation.components.AppSwitch
import com.example.timemanager.presentation.components.AppTextField
import com.example.timemanager.presentation.components.AppTextButton
import com.example.timemanager.presentation.theme.OnSurfaceVariant
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.PrimaryButtonContainer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(
                when {
                    event.type == ScheduledEventType.BIRTHDAY -> R.drawable.ic_redeem
                    event.hasAlarm -> R.drawable.ic_alarm
                    event.time != null -> R.drawable.ic_schedule
                    else -> R.drawable.ic_event_note
                }
            ),
            contentDescription = null,
            tint = if (isPast) OnSurfaceVariant.copy(alpha = 0.45f) else PrimaryButtonContainer,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                textDecoration = if (isPast) TextDecoration.LineThrough else null
            )
            event.time?.let { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPast) OnSurfaceVariant.copy(alpha = 0.45f) else OnSurfaceVariant
                )
            }
        }
        if (event.hasAlarm) {
            Icon(
                painter = painterResource(R.drawable.ic_alarm),
                contentDescription = stringResource(R.string.event_alarm),
                tint = PrimaryButtonContainer,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.event_delete_description),
                tint = OnSurfaceVariant
            )
        }
    }
}

/**
 * Редактор события: тип (обычное/день рождения), название, время (по желанию)
 * и будильник. Событие без времени — «весь день», будильник для него недоступен.
 * День рождения повторяется каждый год автоматически.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledEventEditorDialog(
    original: ScheduledEvent?,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (ScheduledEvent) -> Unit
) {
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var time by remember { mutableStateOf(original?.time) }
    var alarmEnabled by remember { mutableStateOf(original?.alarmEnabled == true) }
    var type by remember { mutableStateOf(original?.type ?: ScheduledEventType.REGULAR) }
    var showTimePicker by remember { mutableStateOf(false) }

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

            // Тип события: обычное или день рождения (повтор каждый год).
            Text(
                text = stringResource(R.string.event_type),
                style = MaterialTheme.typography.labelLarge,
                color = OnTertiary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilterChip(
                    selected = type == ScheduledEventType.REGULAR,
                    onClick = { type = ScheduledEventType.REGULAR },
                    label = stringResource(R.string.event_type_regular),
                    iconRes = R.drawable.ic_event_note
                )
                AppFilterChip(
                    selected = type == ScheduledEventType.BIRTHDAY,
                    onClick = { type = ScheduledEventType.BIRTHDAY },
                    label = stringResource(R.string.event_type_birthday),
                    iconRes = R.drawable.ic_redeem
                )
            }

            // Поле времени только для чтения: клик открывает выбор времени.
            Box {
                AppTextField(
                    value = time ?: "",
                    onValueChange = { },
                    label = stringResource(R.string.event_time),
                    placeholder = stringResource(R.string.event_time_none),
                    singleLine = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (time != null) {
                            IconButton(
                                onClick = {
                                    time = null
                                    alarmEnabled = false
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.event_time_clear),
                                    tint = OnSurfaceVariant
                                )
                            }
                        }
                    }
                )
                // Прозрачный оверлей: по полю нельзя печатать, но можно кликнуть.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showTimePicker = true }
                )
            }

            if (time != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.event_alarm),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnTertiary
                    )
                    AppSwitch(checked = alarmEnabled, onCheckedChange = { alarmEnabled = it })
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
                            time = time,
                            type = type,
                            alarmEnabled = alarmEnabled && time != null,
                            position = original?.position ?: 0
                        )
                    )
                },
                enabled = title.isNotBlank()
            )
        },
        dismissButton = { AppCancelButton(onClick = onDismiss) }
    )

    if (showTimePicker) {
        EventTimePickerDialog(
            initial = time?.let { LocalTime.parse(it) } ?: LocalTime.of(9, 0),
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                time = String.format(Locale.US, "%02d:%02d", picked.hour, picked.minute)
                showTimePicker = false
            }
        )
    }
}

/** Выбор времени в стиле Material 3 поверх палитры приложения. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    AppDialog(
        title = stringResource(R.string.event_time),
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            AppSaveButton(
                onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }
            )
        },
        dismissButton = { AppCancelButton(onClick = onDismiss) }
    )
}

/**
 * Произошло ли событие уже (для зачёркивания в списке).
 * Повторяющиеся дни рождения не «проходят» — они наступают каждый год.
 */
private fun ScheduledEvent.isPastOccurrence(now: LocalDateTime = LocalDateTime.now()): Boolean {
    if (type == ScheduledEventType.BIRTHDAY) return false
    val day: LocalDate = dateOrNull() ?: return false
    val moment = timeOrNull()
    return if (moment != null) {
        LocalDateTime.of(day, moment).isBefore(now)
    } else {
        day.isBefore(now.toLocalDate())
    }
}

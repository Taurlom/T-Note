package ru.taurlom.tnote.presentation.screens.calendar

import androidx.compose.foundation.background
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.RepeatUnit
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.util.formatEventClock
import ru.taurlom.tnote.presentation.components.AppButton
import ru.taurlom.tnote.presentation.components.AppCheckbox
import ru.taurlom.tnote.presentation.components.AppColorPicker
import ru.taurlom.tnote.presentation.components.AppDialog
import ru.taurlom.tnote.presentation.components.AppDropdown
import ru.taurlom.tnote.presentation.components.AppOutlinedButton
import ru.taurlom.tnote.presentation.components.AppSaveButton
import ru.taurlom.tnote.presentation.components.AppTextButton
import ru.taurlom.tnote.presentation.components.AppTextField
import ru.taurlom.tnote.presentation.components.AppTimePicker
import ru.taurlom.tnote.presentation.components.ColorSwatchOption
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.util.eventTimeLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

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
    onDeleteEvent: (ScheduledEvent) -> Unit,
) {
    var noteText by remember(note) { mutableStateOf(note?.text.orEmpty()) }
    // null — редактор закрыт; ScheduledEvent с id == 0 — новый черновик.
    var editorTarget by remember { mutableStateOf<ScheduledEvent?>(null) }
    val dateKey = date.toIsoString()
    // День, который показывает диалог: зачёркивание событий считается по нему,
    // а не по якорной дате события (у повторяющихся якорь может быть в прошлом).
    val day = remember(date) { LocalDate.of(date.year, date.month, date.day) }
    val locale = LocalConfiguration.current.locales[0]

    AppDialog(
        title = date.toDisplayName(locale),
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = stringResource(R.string.note_label),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            if (events.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.events_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.dialogContent,
                )
                events.forEach { event ->
                    // Этап 2: события Яндекс-календаря правятся и удаляются
                    // здесь же, а синк доезжает до сервера (If-Match/PUT,
                    // tombstone/DELETE). Серверные перезаписи поверх наших
                    // правок прилетают снеком через SyncOverwriteBus.
                    ScheduledEventRow(
                        event = event,
                        day = day,
                        onClick = { editorTarget = event },
                        onDelete = { onDeleteEvent(event) },
                    )
                }
            }

            AppOutlinedButton(
                onClick = { editorTarget = ScheduledEvent(date = dateKey, title = "") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.add_event),
                    modifier = Modifier.padding(start = 8.dp),
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
                enabled = noteText.isNotBlank() || note != null || events.isNotEmpty(),
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
                enabled = note != null || events.isNotEmpty(),
            )
        },
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
            },
        )
    }
}

/** Строка события в диалоге дня. Прошедшие события приглушены и зачёркнуты. */
@Composable
private fun ScheduledEventRow(event: ScheduledEvent, day: LocalDate, onClick: () -> Unit, onDelete: () -> Unit) {
    val isPast = remember(event, day) { event.isPastOccurrence(day) }
    val titleColor = if (isPast) AppTheme.colors.dialogContent.copy(alpha = 0.45f) else AppTheme.colors.dialogContent
    // Часы — по окну показываемого дня: у события длиннее суток в каждый
    // день своя часть («23:30 – 24:00», «00:00 – 00:30»).
    val window = remember(event, day) { event.windowOn(day) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(
                when (event.type) {
                    ScheduledEventType.BIRTHDAY -> R.drawable.ic_event_cake
                    ScheduledEventType.WEEKEND -> R.drawable.ic_weekend
                    else -> event.icon.drawableRes
                },
            ),
            contentDescription = null,
            tint = eventIconColor(event.colorArgb, isPast, AppTheme.colors.dialogContent),
            modifier = Modifier.size(20.dp),
        )
        // Часы события — между иконкой и названием: «10:00 – 11:30 Ремонт».
        eventTimeLabel(window)?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isPast) {
                    AppTheme.colors.dialogContent.copy(alpha = 0.45f)
                } else {
                    AppTheme.colors.dialogContentMuted
                },
            )
        }
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            textDecoration = if (isPast) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (event.remoteImported) {
            // Метка источника: крайняя версия содержимого приехала вычиткой
            // с сервера. Собственное событие, отправленное в подписку, метки
            // не получает — caldavAccountId у него есть, а происхождение своё.
            Text(
                text = stringResource(R.string.sync_badge),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.dialogContent.copy(alpha = 0.6f),
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.event_delete_description),
                tint = AppTheme.colors.dialogContent,
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
 * Время (начало и завершение) задают обычные и повторяющиеся события —
 * выбор часов тот же, что у напоминания в настройках.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledEventEditorDialog(
    original: ScheduledEvent?,
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (ScheduledEvent) -> Unit,
    onDelete: () -> Unit,
) {
    // Весь черновик события — в Bundle: пересоздание Activity (тема,
    // язык, масштаб шрифта) не сбрасывает набранное. Енамы сохраняются
    // как Serializable, числа и строки — напрямую.
    var title by rememberSaveable { mutableStateOf(original?.title.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(original?.type ?: ScheduledEventType.REGULAR) }
    var icon by rememberSaveable { mutableStateOf(original?.icon ?: EventIcon.NOTE) }
    var colorArgb by rememberSaveable {
        mutableStateOf(original?.colorArgb ?: ScheduledEvent.DEFAULT_COLOR)
    }
    // Повторение в духе Яндекса: частота + «каждые N» + дни недели
    // (для недель) + «повторять до» (пусто — бессрочно).
    var repeatUnit by rememberSaveable {
        mutableStateOf(original?.repeatUnit ?: RepeatUnit.DAYS)
    }
    var intervalText by rememberSaveable {
        mutableStateOf(original?.repeatInterval?.takeIf { it > 0 }?.toString() ?: "1")
    }
    val anchorDayOfWeek = remember(original, defaultDate) {
        (original?.dateOrNull() ?: runCatching { LocalDate.parse(defaultDate) }.getOrNull() ?: LocalDate.now())
            .dayOfWeek
    }
    var weekdays by rememberSaveable {
        mutableStateOf(
            original?.repeatWeekdays?.takeIf { it.isNotEmpty() } ?: setOf(anchorDayOfWeek),
        )
    }
    var untilText by rememberSaveable { mutableStateOf(original?.repeatUntil.orEmpty()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var hidePast by rememberSaveable { mutableStateOf(original?.hidePastOccurrences ?: false) }
    // Время события: обычное и повторяющееся могут иметь часы, день рождения
    // и «Выходной» остаются дневными метками. Минуты от полуночи, -1 —
    // времени нет (в модели это null).
    var startMinutes by rememberSaveable { mutableStateOf(original?.startMinutes ?: NO_TIME) }
    var endMinutes by rememberSaveable { mutableStateOf(original?.endMinutes ?: NO_TIME) }
    // Какое из полей сейчас выбирают: null — пикер закрыт.
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }

    val interval = intervalText.toIntOrNull()?.takeIf { it > 0 }
    val untilDate = untilText.takeIf { it.isNotBlank() }
        ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
    // Время события в модели — null, а в состоянии редактора — -1 (см. NO_TIME).
    val startTime = startMinutes.takeIf { it >= 0 }
    val endTime = endMinutes.takeIf { it >= 0 }
    // Порядок времени: завершение раньше начала в одних сутках — ошибка
    // ввода, сохранить такое событие нельзя. У чужого события длиннее
    // суток конец приходит на другой день — это не ошибка.
    val timeRangeInvalid = isTimeRangeInvalid(
        date = original?.date ?: defaultDate,
        startMinutes = startTime,
        endMinutes = endTime,
        endDate = original?.endDate,
    )
    // Повторяющемуся событию нужны частота, интервал и (если заполнено)
    // разборчивая дата-ограничитель.
    val repeatConfigured = type != ScheduledEventType.REPEATING ||
        (repeatUnit != null && interval != null && (untilText.isBlank() || untilDate != null))

    AppDialog(
        title = stringResource(
            if (original == null) R.string.add_event else R.string.edit_event,
        ),
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.event_name),
                singleLine = true,
                required = true,
                modifier = Modifier.fillMaxWidth(),
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
                        color = AppTheme.colors.dialogContent,
                    )
                },
                optionIcon = { option ->
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = null,
                        tint = AppTheme.colors.dialogContent,
                    )
                },
                onSelect = { type = it },
            )

            if (type == ScheduledEventType.REPEATING) {
                // Частота — тем же меню, что и в Яндексе.
                AppDropdown(
                    label = stringResource(R.string.repeat_frequency),
                    selectedLabel = stringResource(repeatUnit.labelRes),
                    options = RepeatUnit.entries.toList(),
                    optionText = { option ->
                        Text(
                            text = stringResource(option.labelRes),
                            color = AppTheme.colors.dialogContent,
                        )
                    },
                    onSelect = {
                        repeatUnit = it
                        // Для недель без выбранных дней берём день якоря.
                        if (it == RepeatUnit.WEEKS && weekdays.isEmpty()) {
                            weekdays = setOf(anchorDayOfWeek)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                    label = stringResource(R.string.repeat_every),
                    singleLine = true,
                    // Без интервала повторяющее событие не существует.
                    required = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (repeatUnit == RepeatUnit.WEEKS) {
                    Text(
                        text = stringResource(R.string.repeat_weekdays),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.colors.dialogContent,
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            WeekdayChip(
                                day = day,
                                selected = day in weekdays,
                                locale = LocalConfiguration.current.locales[0],
                                onClick = {
                                    weekdays = if (day in weekdays) {
                                        weekdays - day
                                    } else {
                                        weekdays + day
                                    }
                                },
                            )
                        }
                    }
                }

                // «Повторять до»: бессрочно или дата (выбор в системном
                // календаре). Выбранная дата показывается в поле.
                AppDropdown(
                    label = stringResource(R.string.repeat_until),
                    selectedLabel = untilDate?.let { date ->
                        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    } ?: stringResource(R.string.repeat_until_never),
                    options = listOf(R.string.repeat_until_never, R.string.repeat_until_pick),
                    optionText = { option ->
                        Text(
                            text = stringResource(option),
                            color = AppTheme.colors.dialogContent,
                        )
                    },
                    onSelect = { option ->
                        if (option == R.string.repeat_until_pick) {
                            showDatePicker = true
                        } else {
                            untilText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Флажок без 48-dp минимальной зоны касания Material — иначе
                // визуальный квадратик центрируется в ней и смещён вправо, не
                // сходясь с краём полей. В material3 1.3.x старый
                // LocalMinimumInteractiveComponentEnforcement компонентами уже
                // не читается, управляем через размер.
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppCheckbox(
                            checked = hidePast,
                            onCheckedChange = { hidePast = it },
                            onDarkBackground = false,
                            // Приглушённый цвет по умолчанию сливался бы с бежевым фоном.
                            uncheckedColor = AppTheme.colors.dialogContent,
                        )
                        Text(
                            text = stringResource(R.string.repeat_hide_past),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.dialogContent,
                        )
                    }
                }
            }

            // Иконку и её цвет выбирают обычные и повторяющиеся события:
            // день рождения отмечается подарком, «Выходной» — подсветкой дня.
            if (type == ScheduledEventType.REGULAR || type == ScheduledEventType.REPEATING) {
                // Время события: тот же выбор часов, что у напоминания в
                // настройках (пикер времени в отдельном диалоге).
                EventTimeDropdown(
                    label = stringResource(R.string.event_time_start),
                    value = startTime,
                    onPick = { timeTarget = TimeTarget.START },
                    onClear = { startMinutes = NO_TIME },
                )
                EventTimeDropdown(
                    label = stringResource(R.string.event_time_end),
                    value = endTime,
                    onPick = { timeTarget = TimeTarget.END },
                    onClear = { endMinutes = NO_TIME },
                )
                // Ошибка ввода времени — под полями: завершение раньше
                // начала в одних сутках сохранить нельзя.
                if (timeRangeInvalid) {
                    Text(
                        text = stringResource(R.string.event_time_range_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = stringResource(R.string.event_icon),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.dialogContent,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EventIcon.entries.forEach { option ->
                        EventIconOption(
                            icon = option,
                            selected = icon == option,
                            onClick = { icon = option },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.event_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.dialogContent,
                )
                AppColorPicker(
                    options = buildList {
                        // Первая плашка — «по умолчанию»: цвет маркера события в сетке.
                        add(
                            ColorSwatchOption(
                                value = ScheduledEvent.DEFAULT_COLOR,
                                swatch = AppTheme.colors.calendarEventMarker,
                                label = stringResource(R.string.color_default),
                            ),
                        )
                        eventColorPresets.forEach { preset ->
                            add(ColorSwatchOption(value = preset, swatch = Color(preset)))
                        }
                    },
                    selected = colorArgb,
                    onSelect = { colorArgb = it },
                )
            }
        },
        confirmButton = {
            AppSaveButton(
                onClick = {
                    onSave(
                        ScheduledEvent(
                            id = original?.id ?: 0L,
                            // UID и происхождение переживают правку: они —
                            // ключ сопоставления с серверным объектом.
                            uid = original?.uid ?: UUID.randomUUID().toString(),
                            caldavAccountId = original?.caldavAccountId,
                            // Дату открытого дня передаём только для новых
                            // событий; для сохранённых репозиторий вернёт
                            // якорную дату строки (см. ScheduledEventRepositoryImpl.update).
                            date = original?.date ?: defaultDate,
                            title = title,
                            type = type,
                            icon = icon,
                            colorArgb = colorArgb,
                            repeatUnit = if (type == ScheduledEventType.REPEATING) repeatUnit else null,
                            repeatInterval = if (type == ScheduledEventType.REPEATING) {
                                interval ?: 1
                            } else {
                                1
                            },
                            repeatWeekdays = if (type == ScheduledEventType.REPEATING && repeatUnit == RepeatUnit.WEEKS) {
                                weekdays
                            } else {
                                emptySet()
                            },
                            repeatUntil = if (type == ScheduledEventType.REPEATING) {
                                untilDate?.toString()
                            } else {
                                null
                            },
                            // Отмены вхождений и метки времени — не для
                            // редактора: их прикладывает сервер. Правка
                            // сохранённого события обязана их не потерять.
                            exdates = original?.exdates ?: emptySet(),
                            createdAt = original?.createdAt,
                            modifiedAt = original?.modifiedAt,
                            // Время — только у обычных и повторяющихся: день
                            // рождения и «Выходной» остаются дневными метками.
                            startMinutes = if (type.hasClockTime()) startTime else null,
                            endMinutes = if (type.hasClockTime()) endTime else null,
                            // Дата конца — серверные данные: правка не должна
                            // их терять (как отмены вхождений). Свои события
                            // в одних сутках — у них даты конца нет.
                            endDate = original?.endDate,
                            hidePastOccurrences =
                            type == ScheduledEventType.REPEATING && hidePast,
                            position = original?.position ?: 0,
                        ),
                    )
                },
                enabled = title.isNotBlank() && repeatConfigured && !timeRangeInvalid,
            )
        },
        dismissButton = if (original != null) {
            // Удаление только этого события, день остаётся.
            { AppTextButton(onClick = onDelete, textRes = R.string.delete) }
        } else {
            null
        },
    )

    if (showDatePicker) {
        // Системный выбор даты для «повторять до». Миллисекунды пикера —
        // UTC-полуночь выбранного дня; обратно собираем LocalDate.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (untilDate ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            untilText = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    },
                    textRes = R.string.save,
                )
            },
            dismissButton = {
                AppTextButton(onClick = { showDatePicker = false }, textRes = R.string.cancel)
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    timeTarget?.let { target ->
        // Выбор часов события — тот же пикер, что у напоминания в настройках.
        val current = when (target) {
            TimeTarget.START -> startTime
            TimeTarget.END -> endTime
        }
        // Значение по умолчанию: начало — 09:00, конец — час после начала
        // (у события без начала конец отсчитывается от девяти).
        val fallback = when (target) {
            TimeTarget.START -> DEFAULT_START_MINUTES
            TimeTarget.END -> ((startTime ?: DEFAULT_START_MINUTES) + 60) % MINUTES_PER_DAY
        }
        val initial = current ?: fallback
        val timeState = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AppDialog(
            title = stringResource(
                if (target == TimeTarget.START) {
                    R.string.event_time_dialog_start
                } else {
                    R.string.event_time_dialog_end
                },
            ),
            onDismissRequest = { timeTarget = null },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        val picked = timeState.hour * 60 + timeState.minute
                        when (target) {
                            TimeTarget.START -> startMinutes = picked
                            TimeTarget.END -> endMinutes = picked
                        }
                        timeTarget = null
                    },
                    textRes = R.string.done,
                )
            },
            // Время снимается тем же диалогом — отдельной кнопки «Убрать»
            // в редакторе не нужно.
            dismissButton = if (current != null) {
                {
                    AppTextButton(
                        onClick = {
                            when (target) {
                                TimeTarget.START -> startMinutes = NO_TIME
                                TimeTarget.END -> endMinutes = NO_TIME
                            }
                            timeTarget = null
                        },
                        textRes = R.string.event_time_clear,
                    )
                }
            } else {
                null
            },
            text = {
                AppTimePicker(state = timeState)
            },
        )
    }
}

/**
 * Какое из полей времени редактируется: начало или завершение события.
 */
private enum class TimeTarget { START, END }

/**
 * Час по умолчанию для начала события — 09:00.
 */
private const val DEFAULT_START_MINUTES = 9 * 60

/** Минут в сутках — границы времени события. */
private const val MINUTES_PER_DAY = 24 * 60

/**
 * В состоянии редактора время хранится как минуты от полуночи, а -1 означает
 * «времени нет» (в модели это null). rememberSaveable не умеет хранить
 * null как Int, поэтому пустое значение — sentinel.
 */
private const val NO_TIME = -1

/**
 * Умеет ли тип события часы: обычное и повторяющееся — да, день рождения и
 * «Выходной» остаются дневными метками.
 */
private fun ScheduledEventType.hasClockTime(): Boolean =
    this == ScheduledEventType.REGULAR || this == ScheduledEventType.REPEATING

/**
 * Поле времени события: выбранные часы или «Не задано». Раскрывающийся
 * список — тот же приём, что у «Повторять до»: «Убрать время» снимает
 * часы, «Выбрать время…» открывает диалог с пикером.
 */
@Composable
private fun EventTimeDropdown(
    label: String,
    value: Int?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    // Без времени снимать нечего — в меню только выбор нового.
    val options = if (value == null) {
        listOf(TimeChoice.PICK)
    } else {
        listOf(TimeChoice.CLEAR, TimeChoice.PICK)
    }
    AppDropdown(
        label = label,
        selectedLabel = value?.let { formatEventClock(it) } ?: stringResource(R.string.event_time_unset),
        options = options,
        optionText = { choice ->
            Text(
                text = stringResource(choice.labelRes),
                color = AppTheme.colors.dialogContent,
            )
        },
        onSelect = { choice ->
            when (choice) {
                TimeChoice.CLEAR -> onClear()
                TimeChoice.PICK -> onPick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Пункты меню поля времени. */
private enum class TimeChoice(val labelRes: Int) {
    CLEAR(R.string.event_time_clear),
    PICK(R.string.event_time_pick),
}

/** Кружок выбора дня недели — в размере и стилистике палитры цветов. */
@Composable
private fun WeekdayChip(day: DayOfWeek, selected: Boolean, locale: Locale, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (selected) AppTheme.colors.chipSelectedContainer else AppTheme.colors.chipContainer,
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            // Короткое имя дня в локали: «пн», «вт»… / «Mon», «Tue»…
            text = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).lowercase(locale),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AppTheme.colors.chipSelectedContent else AppTheme.colors.chipContent,
        )
    }
}

/** Кружок-переключатель иконки события — в размер палитры цветов. */
@Composable
private fun EventIconOption(icon: EventIcon, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    colors.chipSelectedContainer
                } else {
                    colors.chipContainer
                },
            )
            .clickable(
                onClickLabel = stringResource(icon.labelRes),
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon.drawableRes),
            contentDescription = stringResource(icon.labelRes),
            tint = if (selected) colors.chipSelectedContent else colors.chipContent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Произошло ли уже событие, показанное в день [day] (для зачёркивания в
 * списке). Сравнивается именно показанный день, а не якорная дата события:
 * у повторяющегося события якорь может быть в прошлом, а вхождения — впереди.
 * Дни рождения и пометки «Выходной» не «проходят»: они отмечают день.
 */
internal fun ScheduledEvent.isPastOccurrence(day: LocalDate, today: LocalDate = LocalDate.now()): Boolean {
    if (type == ScheduledEventType.BIRTHDAY || type == ScheduledEventType.WEEKEND) {
        return false
    }
    return day.isBefore(today)
}

/**
 * Ошибка ввода времени события: «Завершение» раньше «Начала» в одних
 * сутках — сохранить такое событие нельзя.
 *
 * Закон доменный ([ru.taurlom.tnote.domain.model.ScheduledEvent.hasValidTimeRange]):
 * у чужого события длиннее суток конец приходит на другой день (endDate) —
 * это не ошибка, а его длительность.
 */
internal fun isTimeRangeInvalid(
    date: String,
    startMinutes: Int?,
    endMinutes: Int?,
    endDate: String? = null,
): Boolean = !ScheduledEvent(
    date = date,
    title = "",
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    endDate = endDate,
).hasValidTimeRange()

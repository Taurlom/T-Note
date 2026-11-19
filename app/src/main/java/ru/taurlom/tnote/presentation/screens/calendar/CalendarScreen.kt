package ru.taurlom.tnote.presentation.screens.calendar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import ru.taurlom.tnote.presentation.components.AppTabs
import ru.taurlom.tnote.presentation.components.SectionTopBar
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.util.eventTimeLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(showBrandHeader: Boolean, viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val caldavAccount by viewModel.caldavAccount.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var selectedDate by remember { mutableStateOf<CalendarDate?>(null) }
    val scrollState = rememberScrollState()
    val locale = LocalConfiguration.current.locales[0]

    // Итог кнопки ⇄ в шапке — тост, как у «Обновить» в настройках.
    CaldavSyncResultToast(viewModel)

    // Дистанция вертикального свайпа, после которой меняется период.
    val swipeDistancePx = with(LocalDensity.current) { 96.dp.toPx() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Нижний бар рендерится под пейджером разделов (MainTabsScreen).
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            SectionTopBar(
                title = stringResource(R.string.calendar_title),
                showBrandHeader = showBrandHeader,
                scrollBehavior = scrollBehavior,
                // Синхронизация из шапки — только при подключённом календаре.
                onSyncClick = caldavAccount?.let { { viewModel.syncCaldav() } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Табы прижаты выше: вертикальные поля меньше горизонтальных.
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // Свайп листает периоды только в режиме «Месяц». В остальных
                // режимах под сеткой есть прокручиваемый список событий, и
                // перехват вертикальных жестов мешал бы ему.
                .then(
                    if (uiState.mode == CalendarViewMode.MONTH) {
                        Modifier.pointerInput(viewModel) {
                            var draggedPx = 0f
                            detectVerticalDragGestures(
                                onDragStart = { draggedPx = 0f },
                                onDragCancel = { draggedPx = 0f },
                                onDragEnd = {
                                    when {
                                        draggedPx < -swipeDistancePx ->
                                            viewModel.onEvent(CalendarEvent.ShiftPeriod(1))

                                        draggedPx > swipeDistancePx ->
                                            viewModel.onEvent(CalendarEvent.ShiftPeriod(-1))
                                    }
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                draggedPx += dragAmount
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (uiState.mode == CalendarViewMode.MONTH) {
                        Modifier
                    } else {
                        Modifier.verticalScroll(scrollState)
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalendarModeTabs(uiState.mode) { mode ->
                viewModel.onEvent(CalendarEvent.ChangeMode(mode))
            }

            CalendarHeader(
                state = uiState,
                locale = locale,
                onPrevious = { viewModel.onEvent(CalendarEvent.ShiftPeriod(-1)) },
                onNext = { viewModel.onEvent(CalendarEvent.ShiftPeriod(1)) },
            )

            when (uiState.mode) {
                CalendarViewMode.MONTH -> {
                    WeekDayLabels()
                    CalendarGrid(
                        yearMonth = uiState.yearMonth,
                        notes = uiState.notes,
                        events = uiState.events,
                        weekendDates = uiState.weekendDates,
                        onDayClick = { selectedDate = it },
                    )
                }

                CalendarViewMode.WEEK -> {
                    val dates = visibleDates(uiState)
                    WeekGrid(
                        dates = dates,
                        notes = uiState.notes,
                        events = uiState.events,
                        weekendDates = uiState.weekendDates,
                        onDayClick = { selectedDate = it },
                    )
                    EventsSection(
                        title = stringResource(R.string.calendar_week_events),
                        days = dates.map { it to uiState.events[it.toString()].orEmpty() },
                        notes = uiState.notes,
                    )
                }

                CalendarViewMode.DAY -> {
                    val day = uiState.anchorDate
                    EventsSection(
                        title = stringResource(R.string.calendar_day_events),
                        days = listOf(day to uiState.events[day.toString()].orEmpty()),
                        notes = uiState.notes,
                    )
                }
            }
        }
    }

    selectedDate?.let { date ->
        val dateKey = date.toIsoString()
        CalendarDayDialog(
            date = date,
            note = uiState.notes[dateKey],
            events = uiState.events[dateKey].orEmpty(),
            provider = caldavAccount?.provider,
            onDismiss = { selectedDate = null },
            onSaveNote = { viewModel.onEvent(CalendarEvent.SaveNote(dateKey, it)) },
            onDeleteDay = { viewModel.onEvent(CalendarEvent.DeleteDay(dateKey)) },
            onAddEvent = { viewModel.onEvent(CalendarEvent.AddEvent(dateKey, it)) },
            onUpdateEvent = { viewModel.onEvent(CalendarEvent.UpdateEvent(it)) },
            onDeleteEvent = { viewModel.onEvent(CalendarEvent.DeleteEvent(it)) },
        )
    }
}

/** Все даты показанного периода — по одной на день недели. */
private fun visibleDates(state: CalendarUiState): List<LocalDate> {
    val (start, end) = state.visibleDateRange()
    return generateSequence(start) { date ->
        if (date < end) date.plusDays(1) else null
    }.toList()
}

/**
 * Итог кнопки ⇄ в шапке — тост с числами (как у «Обновить» в настройках),
 * а при отказе — причина: у календаря нет карточки аккаунта, которая
 * показывала бы lastError сама.
 */
@Composable
private fun CaldavSyncResultToast(viewModel: CalendarViewModel) {
    val result by viewModel.caldavResult.collectAsStateWithLifecycle()
    val error by viewModel.caldavError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(result, error) {
        val text = when {
            result != null -> context.getString(
                R.string.sync_result,
                result?.added ?: 0,
                result?.updated ?: 0,
                result?.removed ?: 0,
                result?.pushed ?: 0,
            )

            error is CaldavSyncException.Network -> context.getString(R.string.sync_error_network)

            error is CaldavSyncException.Auth -> context.getString(R.string.sync_error_auth)

            error != null -> context.getString(R.string.sync_error_other, error?.message ?: "")

            else -> null
        }
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.caldavResultShown()
        }
    }
}

@Composable
private fun CalendarModeTabs(mode: CalendarViewMode, onModeChange: (CalendarViewMode) -> Unit) {
    val modes = CalendarViewMode.values()
    AppTabs(
        titles = listOf(
            stringResource(R.string.calendar_mode_month),
            stringResource(R.string.calendar_mode_week),
            stringResource(R.string.calendar_mode_day),
        ),
        selectedTab = mode.ordinal,
        onSelect = { onModeChange(modes[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CalendarHeader(state: CalendarUiState, locale: Locale, onPrevious: () -> Unit, onNext: () -> Unit) {
    val title = when (state.mode) {
        CalendarViewMode.MONTH -> state.yearMonth.toDisplayName(locale)

        CalendarViewMode.WEEK -> {
            val (start, end) = state.visibleDateRange()
            weekRangeDisplayName(start, end, locale)
        }

        CalendarViewMode.DAY -> state.anchorDate.toDayMonth(locale)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.prev_period),
                tint = AppTheme.colors.calendarHeader,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = AppTheme.colors.calendarHeader,
        )
        IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.next_period),
                tint = AppTheme.colors.calendarHeader,
            )
        }
    }
}

@Composable
private fun WeekDayLabels() {
    // Неделя начинается с понедельника во всех локалях приложения.
    val weekDays = listOf(
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat),
        stringResource(R.string.weekday_sun),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.colors.calendarWeekdayLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Ячейка сетки: `isAdjacentMonth` указывает на день из соседнего месяца. */
private data class CalendarCell(val date: CalendarDate, val dateKey: String, val isAdjacentMonth: Boolean = false)

@Composable
private fun CalendarGrid(
    yearMonth: CalendarYearMonth,
    notes: Map<String, CalendarNote>,
    events: Map<String, List<ScheduledEvent>>,
    weekendDates: Set<String>,
    onDayClick: (CalendarDate) -> Unit,
) {
    // Без remember: вызов дёшев, зато «сегодня» не замирает на первой
    // композиции, если процесс пережил полночь.
    val today = LocalDate.now()

    // Даты и их ISO-ключи пересчитываются только при смене месяца: раньше на
    // каждую перерисовку создавалось ~42 объекта и столько же строк.
    val cells = remember(yearMonth) { buildMonthCells(yearMonth) }
    val noteDates = remember(notes) { notes.keys }
    val eventIconsByDate = remember(events) {
        events.mapValues { (_, list) ->
            list.filter { it.type == ScheduledEventType.REGULAR }
                .map { it.icon to it.colorArgb }
                .distinct()
                .take(3)
        }
    }
    // Иконка повторяющегося события ставится отдельно — в правом верхнем
    // углу ячейки. Одно вхождение — иконка самого события; два и больше —
    // общий знак «Chronic» золотого цвета.
    val recurringIconByDate = remember(events) {
        events.mapNotNull { (date, list) ->
            val recurring = list.filter { it.type == ScheduledEventType.REPEATING }
            when {
                recurring.size >= 2 ->
                    date to (R.drawable.ic_event_chronic to ScheduledEvent.DEFAULT_COLOR)

                recurring.size == 1 -> recurring[0].let {
                    date to (it.icon.drawableRes to it.colorArgb)
                }

                else -> null
            }
        }.toMap()
    }
    val birthdayDates = remember(events) {
        events.filterValues { list ->
            list.any { it.type == ScheduledEventType.BIRTHDAY }
        }.keys
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val cellDate = LocalDate.of(cell.date.year, cell.date.month, cell.date.day)
                    // Как выходные подсвечиваются Сб/Вс и дни с событием «Выходной».
                    // Набор помеченных дат глобальный — подсветка видна и на днях
                    // из соседнего месяцев, хотя они и неактивны.
                    val isWeekend = cellDate.dayOfWeek == DayOfWeek.SATURDAY ||
                        cellDate.dayOfWeek == DayOfWeek.SUNDAY ||
                        weekendDates.contains(cell.dateKey)
                    DayCell(
                        dayNumber = cell.date.day,
                        hasNote = noteDates.contains(cell.dateKey),
                        eventIcons = eventIconsByDate[cell.dateKey].orEmpty(),
                        recurringIcon = recurringIconByDate[cell.dateKey],
                        isBirthday = birthdayDates.contains(cell.dateKey),
                        isWeekend = isWeekend,
                        isToday = cellDate == today,
                        // Отметки прошедших дней визуально гаснут (calendarPastMarker).
                        isPast = cellDate.isBefore(today),
                        isAdjacentMonth = cell.isAdjacentMonth,
                        onClick = { onDayClick(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun buildMonthCells(yearMonth: CalendarYearMonth): List<CalendarCell> {
    val daysInMonth = yearMonth.daysInMonth()
    val offset = yearMonth.firstDayOfWeekOffset()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    // Получаем количество дней в предыдущем месяце
    val prevMonth = yearMonth.plusMonths(-1)
    val daysInPrevMonth = prevMonth.daysInMonth()

    // Создаем список ячеек
    val cells = mutableListOf<CalendarCell>()

    // Дни из предыдущего месяца (заполняют начало первой недели)
    for (i in offset downTo 1) {
        val date = CalendarDate(prevMonth.year, prevMonth.month, daysInPrevMonth - i + 1)
        cells.add(CalendarCell(date = date, dateKey = date.toIsoString(), isAdjacentMonth = true))
    }

    // Дни текущего месяца
    for (day in 1..daysInMonth) {
        val date = CalendarDate(yearMonth.year, yearMonth.month, day)
        cells.add(CalendarCell(date = date, dateKey = date.toIsoString(), isAdjacentMonth = false))
    }

    // Дни следующего месяца (заполняют остаток последней недели)
    val remainingCells = totalCells - cells.size
    if (remainingCells > 0) {
        val nextMonth = yearMonth.plusMonths(1)
        for (day in 1..remainingCells) {
            val date = CalendarDate(nextMonth.year, nextMonth.month, day)
            cells.add(CalendarCell(date = date, dateKey = date.toIsoString(), isAdjacentMonth = true))
        }
    }

    return cells
}

@Composable
private fun DayCell(
    dayNumber: Int,
    hasNote: Boolean,
    eventIcons: List<Pair<EventIcon, Long>>,
    recurringIcon: Pair<Int, Long>?,
    isBirthday: Boolean,
    isWeekend: Boolean,
    isToday: Boolean,
    isPast: Boolean = false,
    isAdjacentMonth: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val backgroundColor = when {
        isToday -> colors.calendarTodayContainer
        isWeekend -> colors.calendarWeekendContainer
        else -> colors.calendarCellContainer
    }
    val textColor = when {
        isAdjacentMonth -> colors.calendarAdjacentDayNumber
        isToday -> colors.calendarTodayNumber
        else -> colors.calendarDayNumber
    }
    val hasContent = hasNote ||
        eventIcons.isNotEmpty() ||
        recurringIcon != null ||
        isBirthday
    val shape = MaterialTheme.shapes.small

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(shape)
            .background(backgroundColor)
            // Дни из соседнего месяца неактивны: заметки и события добавляются
            // только в дни своего месяца (у них и так нет контента в этом виде).
            .then(if (isAdjacentMonth) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        // Число остаётся обычным приглушённым тоном: полупрозрачными
        // проходят только иконки и маркеры прошедших дней (см. DayCell Row).
        Text(
            text = dayNumber.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
        // Иконка повторяющегося события — в правом верхнем углу ячейки,
        // отдельно от ряда иконок под числом.
        recurringIcon?.let { (iconRes, colorArgb) ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(R.string.event_type_repeating),
                tint = eventIconColor(colorArgb, isPast, colors.calendarEventMarker),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(12.dp),
            )
        }
        if (hasContent) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
            ) {
                if (isBirthday) {
                    // День рождения: иконка подарка, не «тухнет» — он повторяется.
                    Icon(
                        painter = painterResource(R.drawable.ic_event_cake),
                        contentDescription = stringResource(R.string.event_type_birthday),
                        tint = colors.calendarEventMarker,
                        modifier = Modifier.size(12.dp),
                    )
                }
                // Иконки обычных событий (выбираются в редакторе события).
                // Прошедшие дни помечаются «потушенным» цветом.
                eventIcons.forEach { (icon, colorArgb) ->
                    Icon(
                        painter = painterResource(icon.drawableRes),
                        contentDescription = stringResource(icon.labelRes),
                        tint = eventIconColor(colorArgb, isPast, colors.calendarEventMarker),
                        modifier = Modifier.size(10.dp),
                    )
                }
                if (hasNote) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (isPast) {
                                    colors.calendarPastMarker
                                } else {
                                    colors.calendarNoteMarker
                                },
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

// ── Режим «Неделя» ──

@Composable
private fun WeekGrid(
    dates: List<LocalDate>,
    notes: Map<String, CalendarNote>,
    events: Map<String, List<ScheduledEvent>>,
    weekendDates: Set<String>,
    onDayClick: (CalendarDate) -> Unit,
) {
    val today = LocalDate.now()
    val weekDayNames = listOf(
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat),
        stringResource(R.string.weekday_sun),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        dates.forEachIndexed { index, date ->
            val key = date.toString()
            val dayEvents = events[key].orEmpty()
            val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY ||
                date.dayOfWeek == DayOfWeek.SUNDAY ||
                weekendDates.contains(key)
            WeekDayCell(
                weekDayName = weekDayNames[index],
                dayNumber = date.dayOfMonth,
                isToday = date == today,
                isWeekend = isWeekend,
                isPast = date.isBefore(today),
                hasNote = notes.containsKey(key),
                eventIcons = dayEvents
                    .filter { it.type == ScheduledEventType.REGULAR }
                    .map { it.icon to it.colorArgb },
                recurringCount = dayEvents.count { it.type == ScheduledEventType.REPEATING },
                hasBirthday = dayEvents.any { it.type == ScheduledEventType.BIRTHDAY },
                onClick = { onDayClick(date.toCalendarDate()) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekDayCell(
    weekDayName: String,
    dayNumber: Int,
    isToday: Boolean,
    isWeekend: Boolean,
    isPast: Boolean,
    hasNote: Boolean,
    eventIcons: List<Pair<EventIcon, Long>>,
    recurringCount: Int,
    hasBirthday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val background = if (isToday) colors.calendarTodayContainer else colors.calendarCellContainer
    val numberColor = when {
        isToday -> colors.calendarTodayNumber
        isWeekend -> colors.calendarWeekdayLabel
        else -> colors.calendarDayNumber
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Text(
            text = weekDayName,
            style = MaterialTheme.typography.labelLarge,
            color = colors.calendarWeekdayLabel,
        )
        Text(
            text = dayNumber.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = numberColor,
        )
        // Маркеры под числом: день рождения, иконки событий, повторы, заметка.
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(12.dp),
        ) {
            if (hasBirthday) {
                Icon(
                    painter = painterResource(R.drawable.ic_event_cake),
                    contentDescription = stringResource(R.string.event_type_birthday),
                    tint = colors.calendarEventMarker,
                    modifier = Modifier.size(10.dp),
                )
            }
            eventIcons.take(3).forEach { (icon, colorArgb) ->
                Icon(
                    painter = painterResource(icon.drawableRes),
                    contentDescription = stringResource(icon.labelRes),
                    tint = eventIconColor(colorArgb, isPast, colors.calendarEventMarker),
                    modifier = Modifier.size(12.dp),
                )
            }
            if (recurringCount > 0) {
                Icon(
                    painter = painterResource(R.drawable.ic_event_chronic),
                    contentDescription = stringResource(R.string.event_type_repeating),
                    tint = eventIconColor(
                        ScheduledEvent.DEFAULT_COLOR,
                        isPast,
                        colors.calendarEventMarker,
                    ),
                    modifier = Modifier.size(10.dp),
                )
            }
            if (hasNote) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isPast) colors.calendarPastMarker else colors.calendarNoteMarker,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

// ── Карточки событий и заметок (режимы «Неделя» и «День») ──

@Composable
private fun EventsSection(title: String, days: List<Pair<LocalDate, List<ScheduledEvent>>>, notes: Map<String, CalendarNote>) {
    val colors = AppTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    // День показывается, если в нём есть событие или заметка.
    val daysWithContent = days.filter { (date, events) ->
        events.isNotEmpty() || notes[date.toString()]?.text?.isNotBlank() == true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.calendarHeader,
        )
        if (daysWithContent.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_no_events),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.calendarPastMarker,
            )
        } else {
            daysWithContent.forEach { (date, events) ->
                val dateKey = date.toString()
                val isPast = date.isBefore(today)
                val note = notes[dateKey]?.text
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = date.toWeekdayDateDisplayName(locale),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.calendarHeader,
                    )
                    // Заметка — отдельная карточка, идёт первой: у неё нет времени.
                    if (!note.isNullOrBlank()) {
                        NoteCard(text = note, isPast = isPast)
                    }
                    sortedDayEvents(events, date).forEach { event ->
                        EventCard(event = event, day = date, isPast = isPast)
                    }
                }
            }
        }
    }
}

/**
 * Порядок карточек дня: сначала без времени, затем с временем по возрастанию
 * времени начала. Внутри одной группы сохраняется исходный порядок.
 */
private fun sortedDayEvents(events: List<ScheduledEvent>, day: LocalDate): List<ScheduledEvent> {
    fun hasTime(event: ScheduledEvent): Boolean {
        val window = event.windowOn(day) ?: return false
        return window.first != null || window.second != null
    }

    fun startTime(event: ScheduledEvent): Int = event.windowOn(day)?.first ?: Int.MAX_VALUE

    return events.sortedWith(
        compareBy<ScheduledEvent> { hasTime(it) }.thenBy { startTime(it) },
    )
}

/** Иконка события над цветной полоской — левая часть карточки. */
@Composable
private fun CardAccent(iconRes: Int, iconLabel: String?, color: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(56.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = iconLabel,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .background(color),
        )
    }
}

/** Карточка события: без клика и стрелки — только содержимое. */
@Composable
private fun EventCard(event: ScheduledEvent, day: LocalDate, isPast: Boolean) {
    val colors = AppTheme.colors
    val time = eventTimeLabel(event.windowOn(day))
    val accent = eventIconColor(event.colorArgb, isPast, colors.calendarEventMarker)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.calendarWeekendContainer)
            .padding(12.dp),
    ) {
        CardAccent(
            iconRes = event.icon.drawableRes,
            iconLabel = stringResource(event.icon.labelRes),
            color = accent,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
        ) {
            if (time != null) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.calendarEventMarker,
                )
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.calendarDayNumber,
            )
        }
    }
}

/** Заметка дня — такая же карточка, но с иконкой заметки. */
@Composable
private fun NoteCard(text: String, isPast: Boolean) {
    val colors = AppTheme.colors
    val accent = if (isPast) colors.calendarPastMarker else colors.calendarNoteMarker
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.calendarWeekendContainer)
            .padding(12.dp),
    ) {
        CardAccent(
            iconRes = R.drawable.ic_event_note,
            iconLabel = stringResource(R.string.icon_note),
            color = accent,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.icon_note),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.calendarDayNumber,
            )
        }
    }
}

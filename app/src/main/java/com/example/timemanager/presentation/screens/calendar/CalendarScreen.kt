package com.example.timemanager.presentation.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.theme.Accent
import com.example.timemanager.presentation.theme.Background
import com.example.timemanager.presentation.theme.OnPrimaryContainer
import com.example.timemanager.presentation.theme.OnSurfaceVariant
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.PastEventMarker
import com.example.timemanager.presentation.theme.Primary
import com.example.timemanager.presentation.theme.Secondary
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var selectedDate by remember { mutableStateOf<CalendarDate?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Нижний бар рендерится над NavHost в AppNavigation.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.calendar_title),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalendarHeader(
                yearMonth = uiState.yearMonth,
                onPrevious = { viewModel.onEvent(CalendarEvent.PreviousMonth) },
                onNext = { viewModel.onEvent(CalendarEvent.NextMonth) }
            )
            WeekDayLabels()
            CalendarGrid(
                yearMonth = uiState.yearMonth,
                notes = uiState.notes,
                events = uiState.events,
                onDayClick = { selectedDate = it }
            )
        }
    }

    selectedDate?.let { date ->
        val dateKey = date.toIsoString()
        CalendarDayDialog(
            date = date,
            note = uiState.notes[dateKey],
            events = uiState.events[dateKey].orEmpty(),
            onDismiss = { selectedDate = null },
            onSaveNote = { viewModel.onEvent(CalendarEvent.SaveNote(dateKey, it)) },
            onDeleteDay = { viewModel.onEvent(CalendarEvent.DeleteDay(dateKey)) },
            onAddEvent = { viewModel.onEvent(CalendarEvent.AddEvent(dateKey, it)) },
            onUpdateEvent = { viewModel.onEvent(CalendarEvent.UpdateEvent(it)) },
            onDeleteEvent = { viewModel.onEvent(CalendarEvent.DeleteEvent(it)) }
        )
    }
}

@Composable
private fun CalendarHeader(
    yearMonth: CalendarYearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "Предыдущий месяц",
                tint = OnPrimaryContainer
            )
        }
        Text(
            // Название месяца форматируется один раз за перерисовку заголовка.
            text = remember(yearMonth) { yearMonth.toDisplayName() },
            style = MaterialTheme.typography.headlineSmall,
            color = OnPrimaryContainer
        )
        IconButton(onClick = onNext) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = "Следующий месяц",
                tint = OnPrimaryContainer
            )
        }
    }
}

private val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")


@Composable
private fun WeekDayLabels() {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
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
    onDayClick: (CalendarDate) -> Unit
) {
    val today = remember { LocalDate.now() }

    // Даты и их ISO-ключи пересчитываются только при смене месяца: раньше на
    // каждую перерисовку создавалось ~42 объекта и столько же строк.
    val cells = remember(yearMonth) { buildMonthCells(yearMonth) }
    val noteDates = remember(notes) { notes.keys }
    val eventDates = remember(events) {
        events.filterValues { list -> list.any { it.type != ScheduledEventType.BIRTHDAY } }.keys
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
                    DayCell(
                        dayNumber = cell.date.day,
                        hasNote = noteDates.contains(cell.dateKey),
                        hasEvent = eventDates.contains(cell.dateKey),
                        isBirthday = birthdayDates.contains(cell.dateKey),
                        isToday = cellDate == today,
                        // Отметки прошедших дней визуально гаснем (см. PastEventMarker).
                        isPast = cellDate.isBefore(today),
                        isAdjacentMonth = cell.isAdjacentMonth,
                        onClick = { onDayClick(cell.date) },
                        modifier = Modifier.weight(1f)
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
    hasEvent: Boolean,
    isBirthday: Boolean,
    isToday: Boolean,
    isPast: Boolean = false,
    isAdjacentMonth: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isToday) OnSurfaceVariant else Background
    val textColor = if (isAdjacentMonth) Primary else if (isToday) OnTertiary else OnSurfaceVariant
    val hasContent = hasNote || hasEvent || isBirthday
    val shape = MaterialTheme.shapes.small

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyLarge,
                // Число прошедшего дня с записью приглушается сильнее обычного.
                color = if (isPast && !isAdjacentMonth && hasContent && !isBirthday) {
                    OnSurfaceVariant.copy(alpha = 0.45f)
                } else {
                    textColor
                },
                textAlign = TextAlign.Center
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBirthday) {
                    // День рождения: иконка подарка, не «тухнет» — он повторяется.
                    Icon(
                        painter = painterResource(R.drawable.ic_redeem),
                        contentDescription = stringResource(R.string.event_type_birthday),
                        tint = Secondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (hasNote) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            // Прошедшие события помечаются «потушенным» цветом.
                            .background(if (isPast) PastEventMarker else Accent, CircleShape)
                    )
                }
                if (hasEvent) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(if (isPast) PastEventMarker else Secondary, CircleShape)
                    )
                }
            }
        }
    }
}

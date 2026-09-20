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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.presentation.theme.Accent
import com.example.timemanager.presentation.theme.AppBarBackground
import com.example.timemanager.presentation.theme.Background
import com.example.timemanager.presentation.theme.OnPrimaryContainer
import com.example.timemanager.presentation.theme.OnSurfaceVariant
import com.example.timemanager.presentation.theme.OnTertiary
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
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBarBackground,
                    titleContentColor = OnTertiary,
                    navigationIconContentColor = OnTertiary,
                    actionIconContentColor = OnTertiary
                )
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
                onDayClick = { selectedDate = it }
            )
        }
    }

    selectedDate?.let { date ->
        val dateKey = date.toIsoString()
        CalendarDayDialog(
            date = date,
            note = uiState.notes[dateKey],
            tasks = uiState.tasks[dateKey].orEmpty(),
            onDismiss = { selectedDate = null },
            onSaveNote = { viewModel.onEvent(CalendarEvent.SaveNote(dateKey, it)) },
            onDeleteDay = { viewModel.onEvent(CalendarEvent.DeleteDay(dateKey)) },
            onAddTask = { viewModel.onEvent(CalendarEvent.AddTask(dateKey, it)) },
            onToggleTask = { viewModel.onEvent(CalendarEvent.ToggleTask(it)) },
            onUpdateTaskText = { viewModel.onEvent(CalendarEvent.UpdateTask(it)) },
            onDeleteTask = { viewModel.onEvent(CalendarEvent.DeleteTask(it)) }
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
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
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
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

/** Ячейка сетки: `date == null` — пустая заглушка до/после первого дня месяца. */
private data class CalendarCell(val date: CalendarDate?, val dateKey: String?)

@Composable
private fun CalendarGrid(
    yearMonth: CalendarYearMonth,
    notes: Map<String, CalendarNote>,
    onDayClick: (CalendarDate) -> Unit
) {
    val today = remember { LocalDate.now() }

    // Даты и их ISO-ключи пересчитываются только при смене месяца: раньше на
    // каждую перерисовку создавалось ~42 объекта и столько же строк.
    val cells = remember(yearMonth) { buildMonthCells(yearMonth) }
    val noteDates = remember(notes) { notes.keys }

    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val date = cell.date
                    if (date != null) {
                        DayCell(
                            dayNumber = date.day,
                            hasNote = noteDates.contains(cell.dateKey),
                            isToday = date.year == today.year &&
                                    date.month == today.monthValue &&
                                    date.day == today.dayOfMonth,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun buildMonthCells(yearMonth: CalendarYearMonth): List<CalendarCell> {
    val daysInMonth = yearMonth.daysInMonth()
    val offset = yearMonth.firstDayOfWeekOffset()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    return List(totalCells) { index ->
        val dayNumber = index - offset + 1
        if (dayNumber in 1..daysInMonth) {
            val date = CalendarDate(yearMonth.year, yearMonth.month, dayNumber)
            CalendarCell(date = date, dateKey = date.toIsoString())
        } else {
            CalendarCell(date = null, dateKey = null)
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    hasNote: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isToday) OnSurfaceVariant else Background
    val textColor = if (isToday) OnTertiary else OnSurfaceVariant
    val shape = RoundedCornerShape(3.dp)

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
                color = textColor,
                textAlign = TextAlign.Center
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasNote) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Accent, CircleShape)
                    )
                }
            }
        }
    }
}

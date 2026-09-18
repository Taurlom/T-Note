package com.example.timemanager.presentation.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.timemanager.R
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.CalendarTask
import com.example.timemanager.presentation.components.BottomNavBar
import com.example.timemanager.presentation.components.BottomNavItem
import com.example.timemanager.presentation.theme.Accent
import com.example.timemanager.presentation.theme.AddButtonBackground
import com.example.timemanager.presentation.theme.AppBarBackground
import com.example.timemanager.presentation.theme.OnSurfaceVariant
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.Secondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var selectedDate by remember { mutableStateOf<CalendarDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "Назад",
                            tint = OnTertiary
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBarBackground,
                    titleContentColor = OnTertiary,
                    navigationIconContentColor = OnTertiary,
                    actionIconContentColor = OnTertiary
                )
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedItem = BottomNavItem.Calendar,
                onItemSelected = { item ->
                    when (item) {
                        BottomNavItem.Documents -> onNavigateToDocuments()
                        BottomNavItem.Settings -> onNavigateToSettings()
                        else -> { /* Calendar уже активен */ }
                    }
                }
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
                tasks = uiState.tasks,
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
                tint = Secondary
            )
        }
        Text(
            text = yearMonth.toDisplayName(),
            style = MaterialTheme.typography.headlineSmall,
            color = Secondary
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Следующий месяц",
                tint = Secondary
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

@Composable
private fun CalendarGrid(
    yearMonth: CalendarYearMonth,
    notes: Map<String, CalendarNote>,
    tasks: Map<String, List<CalendarTask>>,
    onDayClick: (CalendarDate) -> Unit
) {
    val daysInMonth = yearMonth.daysInMonth()
    val offset = yearMonth.firstDayOfWeekOffset()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7

    Column(modifier = Modifier.fillMaxWidth()) {
        for (week in 0 until totalCells / 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayOfWeek in 0..6) {
                    val cellIndex = week * 7 + dayOfWeek
                    val dayNumber = cellIndex - offset + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = CalendarDate(yearMonth.year, yearMonth.month, dayNumber)
                        val dateKey = date.toIsoString()
                        val hasNote = notes.containsKey(dateKey)
                        val hasTasks = tasks[dateKey]?.isNotEmpty() == true
                        DayCell(
                            date = date,
                            hasNote = hasNote,
                            hasTasks = hasTasks,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: CalendarDate,
    hasNote: Boolean,
    hasTasks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
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
                if (hasTasks) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}




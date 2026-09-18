package com.example.timemanager.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.CalendarTask
import com.example.timemanager.domain.usecase.AddCalendarTaskUseCase
import com.example.timemanager.domain.usecase.DeleteCalendarDayUseCase
import com.example.timemanager.domain.usecase.DeleteCalendarTaskUseCase
import com.example.timemanager.domain.usecase.GetCalendarNotesByMonthUseCase
import com.example.timemanager.domain.usecase.GetCalendarTasksByMonthUseCase
import com.example.timemanager.domain.usecase.SaveCalendarNoteUseCase
import com.example.timemanager.domain.usecase.ToggleCalendarTaskUseCase
import com.example.timemanager.domain.usecase.UpdateCalendarTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getNotesByMonthUseCase: GetCalendarNotesByMonthUseCase,
    private val getTasksByMonthUseCase: GetCalendarTasksByMonthUseCase,
    private val saveNoteUseCase: SaveCalendarNoteUseCase,
    private val deleteDayUseCase: DeleteCalendarDayUseCase,
    private val addTaskUseCase: AddCalendarTaskUseCase,
    private val updateTaskUseCase: UpdateCalendarTaskUseCase,
    private val deleteTaskUseCase: DeleteCalendarTaskUseCase,
    private val toggleTaskUseCase: ToggleCalendarTaskUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var monthDataJob: Job? = null

    init {
        loadMonthData()
    }

    private fun loadMonthData() {
        monthDataJob?.cancel()
        val prefix = _uiState.value.yearMonth.monthPrefix()
        monthDataJob = combine(
            getNotesByMonthUseCase(prefix),
            getTasksByMonthUseCase(prefix)
        ) { notes, tasks ->
            CalendarUiState(
                yearMonth = _uiState.value.yearMonth,
                notes = notes.associateBy { it.date },
                tasks = tasks.groupBy { it.date },
                isLoading = false
            )
        }
            .onEach { state -> _uiState.value = state }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: CalendarEvent) {
        when (event) {
            CalendarEvent.PreviousMonth -> shiftMonth(-1)
            CalendarEvent.NextMonth -> shiftMonth(1)
            is CalendarEvent.SaveNote -> saveNote(event.date, event.text)
            is CalendarEvent.DeleteDay -> deleteDay(event.date)
            is CalendarEvent.AddTask -> addTask(event.date, event.text)
            is CalendarEvent.UpdateTask -> updateTask(event.task)
            is CalendarEvent.DeleteTask -> deleteTask(event.task)
            is CalendarEvent.ToggleTask -> toggleTask(event.task)
        }
    }

    private fun shiftMonth(delta: Int) {
        _uiState.update { it.copy(yearMonth = it.yearMonth.plusMonths(delta), isLoading = true) }
        loadMonthData()
    }

    private fun saveNote(date: String, text: String) {
        viewModelScope.launch {
            saveNoteUseCase(CalendarNote(date = date, text = text.trim()))
        }
    }

    private fun deleteDay(date: String) {
        viewModelScope.launch {
            deleteDayUseCase(date)
        }
    }

    private fun addTask(date: String, text: String) {
        viewModelScope.launch {
            val trimmed = text.trim()
            if (trimmed.isBlank()) return@launch
            val dateTasks = _uiState.value.tasks[date].orEmpty()
            val nextPosition = (dateTasks.maxOfOrNull { it.position } ?: -1) + 1
            addTaskUseCase(
                CalendarTask(
                    date = date,
                    text = trimmed,
                    position = nextPosition
                )
            )
        }
    }

    private fun updateTask(task: CalendarTask) {
        viewModelScope.launch {
            updateTaskUseCase(task)
        }
    }

    private fun deleteTask(task: CalendarTask) {
        viewModelScope.launch {
            deleteTaskUseCase(task)
        }
    }

    private fun toggleTask(task: CalendarTask) {
        viewModelScope.launch {
            toggleTaskUseCase(task)
        }
    }
}

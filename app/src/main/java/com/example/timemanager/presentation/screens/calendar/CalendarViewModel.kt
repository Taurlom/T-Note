package com.example.timemanager.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.domain.usecase.AddScheduledEventUseCase
import com.example.timemanager.domain.usecase.DeleteCalendarDayUseCase
import com.example.timemanager.domain.usecase.DeleteScheduledEventUseCase
import com.example.timemanager.domain.usecase.GetBirthdayEventsUseCase
import com.example.timemanager.domain.usecase.GetCalendarNotesByMonthUseCase
import com.example.timemanager.domain.usecase.GetRepeatingEventsUseCase
import com.example.timemanager.domain.usecase.GetScheduledEventsByMonthUseCase
import com.example.timemanager.domain.usecase.SaveCalendarNoteUseCase
import com.example.timemanager.domain.usecase.UpdateScheduledEventUseCase
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
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getNotesByMonthUseCase: GetCalendarNotesByMonthUseCase,
    private val getEventsByMonthUseCase: GetScheduledEventsByMonthUseCase,
    private val getBirthdayEventsUseCase: GetBirthdayEventsUseCase,
    private val getRepeatingEventsUseCase: GetRepeatingEventsUseCase,
    private val saveNoteUseCase: SaveCalendarNoteUseCase,
    private val deleteDayUseCase: DeleteCalendarDayUseCase,
    private val addEventUseCase: AddScheduledEventUseCase,
    private val updateEventUseCase: UpdateScheduledEventUseCase,
    private val deleteEventUseCase: DeleteScheduledEventUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var monthDataJob: Job? = null

    init {
        loadMonthData()
    }

    private fun loadMonthData() {
        monthDataJob?.cancel()
        val yearMonth = _uiState.value.yearMonth
        val prefix = yearMonth.monthPrefix()
        monthDataJob = combine(
            getNotesByMonthUseCase(prefix),
            getEventsByMonthUseCase(prefix),
            getBirthdayEventsUseCase(),
            getRepeatingEventsUseCase()
        ) { notes, monthEvents, birthdays, repeating ->
            CalendarUiState(
                yearMonth = yearMonth,
                notes = notes.associateBy { it.date },
                events = expandEventsForMonth(monthEvents, birthdays, repeating, yearMonth)
            )
        }
            .onEach { state -> _uiState.value = state }
            .launchIn(viewModelScope)
    }

    /**
     * Группирует события по дням отображаемого месяца: обычные берутся как есть,
     * каждый день рождения разворачивается в дату-вхождение текущего года,
     * а повторяющиеся события — во все свои вхождения этого месяца.
     */
    private fun expandEventsForMonth(
        monthEvents: List<ScheduledEvent>,
        birthdays: List<ScheduledEvent>,
        repeating: List<ScheduledEvent>,
        yearMonth: CalendarYearMonth
    ): Map<String, List<ScheduledEvent>> {
        val displayYear = yearMonth.year
        // Вхождения повторяющихся событий генерируются ниже целиком,
        // включая якорное — иначе оно задублировалось бы из месячного запроса.
        val byDate = monthEvents
            .filterNot { it.type == ScheduledEventType.REPEATING }
            .groupByTo(LinkedHashMap()) { it.date }
        birthdays.forEach { birthday ->
            val anchor = birthday.dateOrNull() ?: return@forEach
            // Вхождение за якорный год уже пришло из месячного запроса.
            if (anchor.year == displayYear) return@forEach
            val (month, day) = ScheduledEvent.effectiveBirthdayDate(anchor, displayYear)
            val key = String.format(LOCALE, "%04d-%02d-%02d", displayYear, month, day)
            byDate.getOrPut(key) { mutableListOf() } += birthday.copy(date = key)
        }
        val today = LocalDate.now()
        val first = LocalDate.of(displayYear, yearMonth.month, 1)
        val lastExclusive = first.plusMonths(1)
        repeating.forEach { event ->
            val anchor = event.dateOrNull() ?: return@forEach
            var cursor = maxOf(anchor, first)
            while (cursor.isBefore(lastExclusive)) {
                if (event.occursOn(cursor) &&
                    !(event.hidePastOccurrences && cursor.isBefore(today))
                ) {
                    val key = cursor.toString()
                    byDate.getOrPut(key) { mutableListOf() } += event.copy(date = key)
                }
                cursor = cursor.plusDays(1)
            }
        }
        return byDate.mapValues { (_, events) ->
            events.sortedWith(compareBy({ it.position }, { it.id }))
        }
    }

    fun onEvent(event: CalendarEvent) {
        when (event) {
            CalendarEvent.PreviousMonth -> shiftMonth(-1)
            CalendarEvent.NextMonth -> shiftMonth(1)
            is CalendarEvent.SaveNote -> saveNote(event.date, event.text)
            is CalendarEvent.DeleteDay -> deleteDay(event.date)
            is CalendarEvent.AddEvent -> addEvent(event.date, event.event)
            is CalendarEvent.UpdateEvent -> updateEvent(event.event)
            is CalendarEvent.DeleteEvent -> deleteEvent(event.event)
        }
    }

    private fun shiftMonth(delta: Int) {
        _uiState.update { it.copy(yearMonth = it.yearMonth.plusMonths(delta)) }
        loadMonthData()
    }

    private fun saveNote(date: String, text: String) {
        viewModelScope.launch {
            val trimmed = text.trim()
            // Пустая заметка не создаётся: для очистки дня есть «Удалить».
            if (trimmed.isEmpty()) return@launch
            saveNoteUseCase(CalendarNote(date = date, text = trimmed))
        }
    }

    private fun deleteDay(date: String) {
        viewModelScope.launch {
            deleteDayUseCase(date)
        }
    }

    private fun addEvent(date: String, draft: ScheduledEvent) {
        viewModelScope.launch {
            val trimmed = draft.title.trim()
            if (trimmed.isBlank()) return@launch
            val dateEvents = _uiState.value.events[date].orEmpty()
            val nextPosition = (dateEvents.maxOfOrNull { it.position } ?: -1) + 1
            val id = addEventUseCase(
                draft.copy(date = date, title = trimmed, position = nextPosition)
            )
        }
    }

    private fun updateEvent(event: ScheduledEvent) {
        viewModelScope.launch {
            val trimmed = event.title.trim()
            if (trimmed.isBlank()) return@launch
            val updated = event.copy(title = trimmed)
            updateEventUseCase(updated)
        }
    }

    private fun deleteEvent(event: ScheduledEvent) {
        viewModelScope.launch {
            deleteEventUseCase(event)
        }
    }

    private companion object {
        // Формат ISO-даты не должен зависеть от локали устройства (DefaultLocale).
        val LOCALE: java.util.Locale = java.util.Locale.US
    }
}

package ru.taurlom.tnote.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.model.EventExpander
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import ru.taurlom.tnote.domain.usecase.AddScheduledEventUseCase
import ru.taurlom.tnote.domain.usecase.DeleteCalendarDayUseCase
import ru.taurlom.tnote.domain.usecase.DeleteScheduledEventUseCase
import ru.taurlom.tnote.domain.usecase.GetBirthdayEventsUseCase
import ru.taurlom.tnote.domain.usecase.GetCalendarNotesByMonthUseCase
import ru.taurlom.tnote.domain.usecase.GetRepeatingEventsUseCase
import ru.taurlom.tnote.domain.usecase.GetScheduledEventsByMonthUseCase
import ru.taurlom.tnote.domain.usecase.GetWeekendDatesUseCase
import ru.taurlom.tnote.domain.usecase.ObserveCaldavAccountsUseCase
import ru.taurlom.tnote.domain.usecase.SaveCalendarNoteUseCase
import ru.taurlom.tnote.domain.usecase.SyncCaldavAccountUseCase
import ru.taurlom.tnote.domain.usecase.UpdateScheduledEventUseCase
import ru.taurlom.tnote.presentation.error.launchCatching
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getNotesByMonthUseCase: GetCalendarNotesByMonthUseCase,
    private val getEventsByMonthUseCase: GetScheduledEventsByMonthUseCase,
    private val getBirthdayEventsUseCase: GetBirthdayEventsUseCase,
    private val getRepeatingEventsUseCase: GetRepeatingEventsUseCase,
    private val getWeekendDatesUseCase: GetWeekendDatesUseCase,
    private val saveNoteUseCase: SaveCalendarNoteUseCase,
    private val deleteDayUseCase: DeleteCalendarDayUseCase,
    private val addEventUseCase: AddScheduledEventUseCase,
    private val updateEventUseCase: UpdateScheduledEventUseCase,
    private val deleteEventUseCase: DeleteScheduledEventUseCase,
    private val observeCaldavAccountsUseCase: ObserveCaldavAccountsUseCase,
    private val syncCaldavAccountUseCase: SyncCaldavAccountUseCase,
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
            getRepeatingEventsUseCase(),
            getWeekendDatesUseCase(),
        ) { notes, monthEvents, birthdays, repeating, weekendDates ->
            CalendarUiState(
                yearMonth = yearMonth,
                notes = notes.associateBy { it.date },
                // Разворот вхождений — EventExpander: те же правила живут
                // и в сводке дня (GetEventsForDateUseCase), двух копий
                // закона не держим.
                events = EventExpander.forMonth(
                    monthEvents = monthEvents,
                    birthdays = birthdays,
                    repeating = repeating,
                    year = yearMonth.year,
                    month = yearMonth.month,
                    today = LocalDate.now(),
                ),
                weekendDates = weekendDates,
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
        launchCatching {
            val trimmed = text.trim()
            // Пустая заметка не создаётся: для очистки дня есть «Удалить».
            if (trimmed.isEmpty()) return@launchCatching
            saveNoteUseCase(CalendarNote(date = date, text = trimmed))
        }
    }

    private fun deleteDay(date: String) {
        launchCatching {
            deleteDayUseCase(date)
        }
    }

    private fun addEvent(date: String, draft: ScheduledEvent) {
        launchCatching {
            val trimmed = draft.title.trim()
            if (trimmed.isBlank()) return@launchCatching
            // position заполнит AddScheduledEventUseCase: позиция считается
            // по базе, а не по снапшоту UI (гонка двух быстрых добавлений).
            addEventUseCase(draft.copy(date = date, title = trimmed))
        }
    }

    private fun updateEvent(event: ScheduledEvent) {
        launchCatching {
            val trimmed = event.title.trim()
            if (trimmed.isBlank()) return@launchCatching
            val updated = event.copy(title = trimmed)
            updateEventUseCase(updated)
        }
    }

    private fun deleteEvent(event: ScheduledEvent) {
        launchCatching {
            deleteEventUseCase(event)
        }
    }

    // ── Ручная синхронизация подписки из шапки календаря ──

    /**
     * Подключённый календарь. Один сторонний календарь — поддерживаемая
     * конфигурация (как в настройках), поэтому берём первый; null — кнопка
     * синхронизации в шапке не показывается.
     */
    val caldavAccount: StateFlow<CaldavAccount?> = observeCaldavAccountsUseCase()
        .map { accounts -> accounts.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _caldavBusy = MutableStateFlow(false)
    val caldavBusy: StateFlow<Boolean> = _caldavBusy.asStateFlow()

    /** Итог ручной синхронизации — для тоста; экран гасит через shown(). */
    private val _caldavResult = MutableStateFlow<CaldavSyncResult?>(null)
    val caldavResult: StateFlow<CaldavSyncResult?> = _caldavResult.asStateFlow()

    /** Причина отказа ручной синхронизации — для тоста ошибки. */
    private val _caldavError = MutableStateFlow<CaldavSyncException?>(null)
    val caldavError: StateFlow<CaldavSyncException?> = _caldavError.asStateFlow()

    /** Экран показал результат (тост) — убираем его из состояния. */
    fun caldavResultShown() {
        _caldavResult.value = null
        _caldavError.value = null
    }

    /**
     * Кнопка ⇄ в шапке — та же ручная синхронизация, что «Обновить»
     * в настройках. Отказ доходит тостом: у календаря нет карточки
     * аккаунта, которая показывала бы lastError сама.
     */
    fun syncCaldav() {
        val account = caldavAccount.value ?: return
        if (_caldavBusy.value) return
        viewModelScope.launch {
            _caldavBusy.value = true
            try {
                _caldavResult.value = syncCaldavAccountUseCase(account.id)
            } catch (e: CaldavSyncException) {
                _caldavError.value = e
            }
            _caldavBusy.value = false
        }
    }
}

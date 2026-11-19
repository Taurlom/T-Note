package ru.taurlom.tnote.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
import java.time.YearMonth
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

    private var dataJob: Job? = null

    init {
        loadData()
    }

    /** Заметки сразу всех месяцев видимого периода, одним словарём. */
    private fun notesFlow(months: List<YearMonth>): Flow<Map<String, CalendarNote>> = months.map { month ->
        getNotesByMonthUseCase(month.toMonthPrefix())
            .map { notes -> notes.associateBy { it.date } }
    }.reduce { acc, flow -> acc.combine(flow) { left, right -> left + right } }

    /** События всех месяцев видимого периода, одним списком. */
    private fun eventsFlow(months: List<YearMonth>): Flow<List<ScheduledEvent>> = months.map { getEventsByMonthUseCase(it) }
        .reduce { acc, flow -> acc.combine(flow) { left, right -> left + right } }

    private fun loadData() {
        dataJob?.cancel()
        // Снимок состояния нужен, чтобы перезагрузка данных (в том числе
        // от Room при сохранении) не сбросила вкладку и опорную дату.
        val state = _uiState.value
        val months = state.visibleYearMonths()
        dataJob = combine(
            notesFlow(months),
            eventsFlow(months),
            getBirthdayEventsUseCase(),
            getRepeatingEventsUseCase(),
            getWeekendDatesUseCase(),
        ) { notes, monthEvents, birthdays, repeating, weekendDates ->
            val events = months.fold(emptyMap<String, List<ScheduledEvent>>()) { acc, month ->
                acc + EventExpander.forMonth(
                    monthEvents = monthEvents,
                    birthdays = birthdays,
                    repeating = repeating,
                    year = month.year,
                    month = month.monthValue,
                    today = LocalDate.now(),
                )
            }
            state.copy(
                notes = notes,
                events = events,
                weekendDates = weekendDates,
            )
        }
            .onEach { updated ->
                // Старая подписка может успеть испустить значение между
                // сменой периода и отменой job — не даём ей вернуть старый
                // режим/дату.
                _uiState.update { current ->
                    if (current.mode == updated.mode &&
                        current.anchorDate == updated.anchorDate &&
                        current.yearMonth == updated.yearMonth
                    ) {
                        updated
                    } else {
                        current
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: CalendarEvent) {
        when (event) {
            CalendarEvent.PreviousMonth -> shiftPeriod(-1)
            CalendarEvent.NextMonth -> shiftPeriod(1)
            is CalendarEvent.ChangeMode -> changeMode(event.mode)
            is CalendarEvent.ShiftPeriod -> shiftPeriod(event.delta)
            is CalendarEvent.SaveNote -> saveNote(event.date, event.text)
            is CalendarEvent.DeleteDay -> deleteDay(event.date)
            is CalendarEvent.AddEvent -> addEvent(event.date, event.event)
            is CalendarEvent.UpdateEvent -> updateEvent(event.event)
            is CalendarEvent.DeleteEvent -> deleteEvent(event.event)
        }
    }

    private fun changeMode(mode: CalendarViewMode) {
        val previousMode = _uiState.value.mode
        if (previousMode == mode) return
        _uiState.update { state ->
            when (mode) {
                CalendarViewMode.MONTH -> state.copy(
                    mode = mode,
                    yearMonth = CalendarYearMonth(state.anchorDate.year, state.anchorDate.monthValue),
                )

                else -> {
                    // Из месяца в неделю/день всегда попадаем на сегодняшний
                    // день, а не на первую неделю показанного месяца.
                    val anchor = if (previousMode == CalendarViewMode.MONTH) {
                        LocalDate.now()
                    } else {
                        state.anchorDate
                    }
                    state.copy(mode = mode, anchorDate = anchor)
                }
            }
        }
        loadData()
    }

    private fun shiftPeriod(delta: Int) {
        _uiState.update { state ->
            when (state.mode) {
                CalendarViewMode.MONTH -> {
                    val next = state.yearMonth.plusMonths(delta)
                    state.copy(
                        yearMonth = next,
                        anchorDate = next.toYearMonth().atDay(1),
                    )
                }

                CalendarViewMode.WEEK -> state.copy(anchorDate = state.anchorDate.plusWeeks(delta.toLong()))

                CalendarViewMode.DAY -> state.copy(anchorDate = state.anchorDate.plusDays(delta.toLong()))
            }
        }
        loadData()
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

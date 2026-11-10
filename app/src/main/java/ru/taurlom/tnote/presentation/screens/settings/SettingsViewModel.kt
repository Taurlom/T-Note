package ru.taurlom.tnote.presentation.screens.settings

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.ThemeKind
import ru.taurlom.tnote.domain.repository.BackupDescription
import ru.taurlom.tnote.domain.repository.BackupImportException
import ru.taurlom.tnote.domain.repository.BackupRepository
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import ru.taurlom.tnote.domain.repository.PastCalendarCleanup
import ru.taurlom.tnote.domain.repository.SettingsRepository
import ru.taurlom.tnote.domain.usecase.ClearCalendarUseCase
import ru.taurlom.tnote.domain.usecase.ConnectCaldavAccountUseCase
import ru.taurlom.tnote.domain.usecase.DeletePastCalendarUseCase
import ru.taurlom.tnote.domain.usecase.DisconnectCaldavAccountUseCase
import ru.taurlom.tnote.domain.usecase.DiscoverCaldavCalendarsUseCase
import ru.taurlom.tnote.domain.usecase.ObserveCaldavAccountsUseCase
import ru.taurlom.tnote.domain.usecase.SetReminderTimeUseCase
import ru.taurlom.tnote.domain.usecase.SetRemindersEnabledUseCase
import ru.taurlom.tnote.domain.usecase.SyncCaldavAccountUseCase
import ru.taurlom.tnote.presentation.error.launchCatching
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val setRemindersEnabledUseCase: SetRemindersEnabledUseCase,
    private val setReminderTimeUseCase: SetReminderTimeUseCase,
    private val clearCalendarUseCase: ClearCalendarUseCase,
    private val deletePastCalendarUseCase: DeletePastCalendarUseCase,
    private val backupRepository: BackupRepository,
    private val observeCaldavAccountsUseCase: ObserveCaldavAccountsUseCase,
    private val discoverCaldavCalendarsUseCase: DiscoverCaldavCalendarsUseCase,
    private val connectCaldavAccountUseCase: ConnectCaldavAccountUseCase,
    private val disconnectCaldavAccountUseCase: DisconnectCaldavAccountUseCase,
    private val syncCaldavAccountUseCase: SyncCaldavAccountUseCase,
) : ViewModel() {

    private val backupStatus = MutableStateFlow(BackupStatus())

    /**
     * Диалог «Настройка разделов» живёт здесь, а не в remember на экране:
     * при скрытии раздела пейджер перестраивает страницы, SettingsScreen
     * пересоздаётся и локальный remember терялся — диалог закрывался
     * на первом же чекбоксе.
     */
    private val _sectionsDialogOpen = MutableStateFlow(false)
    val sectionsDialogOpen: StateFlow<Boolean> = _sectionsDialogOpen

    fun openSectionsDialog() {
        _sectionsDialogOpen.value = true
    }
    fun closeSectionsDialog() {
        _sectionsDialogOpen.value = false
    }

    /** Метаданные выбранного файла копии — для диалога подтверждения. */
    private val _pendingImport = MutableStateFlow<BackupDescription?>(null)
    val pendingImport: StateFlow<BackupDescription?> = _pendingImport

    // Язык живёт в состоянии, а не вычисляется в combine: ViewModel
    // переживает пересоздание активности при смене локали, и без явного
    // обновления дропдаун показывал бы старое значение из кэша stateIn.
    private val selectedLanguage = MutableStateFlow(AppLanguage.current())

    private data class BackupStatus(val isBusy: Boolean = false, val result: BackupResult? = null)

    val uiState: StateFlow<SettingsUiState> =
        // Типизированных перегрузок combine() хватает на 5 потоков, а их
        // стало 7 — группируем тройками. Семантика прежняя: любое
        // изменение любого потока пересобирает состояние.
        combine(
            combine(
                settingsRepository.selectedFont,
                settingsRepository.selectedTheme,
                settingsRepository.visibleSections,
            ) { font, theme, sections -> Triple(font, theme, sections) },
            combine(
                settingsRepository.remindersEnabled,
                settingsRepository.reminderTimeMinutes,
                backupStatus,
            ) { remindersEnabled, reminderTime, backup ->
                Triple(remindersEnabled, reminderTime, backup)
            },
            combine(
                selectedLanguage,
                settingsRepository.documentsLockEnabled,
            ) { language, documentsLock -> language to documentsLock },
        ) { appearance, reminders, (language, documentsLockEnabled) ->
            val (font, theme, sections) = appearance
            val (remindersEnabled, reminderTime, backup) = reminders
            SettingsUiState(
                selectedFont = font,
                selectedTheme = theme,
                selectedLanguage = language,
                visibleSections = sections,
                sectionsLoaded = true,
                remindersEnabled = remindersEnabled,
                reminderTimeMinutes = reminderTime,
                remindersLoaded = true,
                isBackupBusy = backup.isBusy,
                backupResult = backup.result,
                documentsLockEnabled = documentsLockEnabled,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    /** Тема применяется сразу, без кнопки «Применить»: она видна мгновенно. */
    fun applyTheme(theme: ThemeKind) {
        launchCatching {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    /**
     * Смена языка интерфейса: AppCompat сам пересоздаёт активность
     * (на Android 13+ это делает система), выбор сохраняется автоматически.
     */
    fun applyLanguage(language: AppLanguage) {
        if (selectedLanguage.value == language) return
        selectedLanguage.value = language
        AppCompatDelegate.setApplicationLocales(
            if (language.tag == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            },
        )
    }

    fun applyFont(font: AppFont) {
        launchCatching {
            settingsRepository.setSelectedFont(font)
        }
    }

    /** Порядок и видимость разделов применяются сразу, как тема. */
    fun updateSections(sections: List<String>) {
        launchCatching {
            settingsRepository.setVisibleSections(sections)
        }
    }

    /** Тумблер напоминаний: включение заводит цепочку, выключение гасит. */
    fun applyRemindersEnabled(enabled: Boolean) {
        launchCatching {
            setRemindersEnabledUseCase(enabled)
        }
    }

    /**
     * Защита «Документов». Пишется только после системного подтверждения —
     * его запускает экран (DeviceCredential), здесь — честное сохранение
     * результата.
     */
    fun applyDocumentsLockEnabled(enabled: Boolean) {
        launchCatching {
            settingsRepository.setDocumentsLockEnabled(enabled)
        }
    }

    /** Смена времени сводки: расписание перестраивается сразу. */
    fun applyReminderTime(minutes: Int) {
        launchCatching {
            setReminderTimeUseCase(minutes)
        }
    }

    fun clearCalendar() {
        launchCatching {
            clearCalendarUseCase()
        }
    }

    /**
     * «Удалить прошедшие»: чище заметки прошедших дней и события без
     * будущих вхождений. [onDone] вызывается при успехе — экран показывает
     * тост со счётчиками; отказ через launchCatching уходит в snackbar.
     */
    fun deletePastCalendar(onDone: (PastCalendarCleanup) -> Unit = {}) {
        launchCatching {
            onDone(deletePastCalendarUseCase())
        }
    }

    fun exportBackup(target: Uri) {
        launchCatching {
            runBackup {
                val summary = backupRepository.exportBackup(target)
                BackupResult.Exported(
                    photos = summary.photos,
                    missing = summary.missingPhotos.size,
                )
            }
        }
    }

    fun importBackup(source: Uri) {
        launchCatching {
            runBackup {
                val summary = backupRepository.importBackup(source)
                BackupResult.Imported(
                    photos = summary.photos,
                    missing = summary.missingPhotos.size,
                )
            }
        }
    }

    /** Читаем шапку копии до подтверждения: пользователь видит, что восстанавливает. */
    fun describeImport(source: Uri) {
        launchCatching {
            try {
                val description = backupRepository.describeBackup(source)
                if (description == null) {
                    backupStatus.value =
                        BackupStatus(result = BackupResult.Failed.NotABackup)
                } else {
                    _pendingImport.value = description
                }
            } catch (e: Exception) {
                // Отказ чтения (SAF отозвал доступ и т.п.) — не «не копия»:
                // показываем честную причину, как у остальных операций.
                backupStatus.value = BackupStatus(result = failure(e))
            }
        }
    }

    fun dismissPendingImport() {
        _pendingImport.value = null
    }

    private suspend fun runBackup(success: (suspend () -> BackupResult)) {
        backupStatus.value = BackupStatus(isBusy = true)
        backupStatus.value = try {
            BackupStatus(result = success())
        } catch (e: Exception) {
            BackupStatus(result = failure(e))
        }
    }

    /**
     * Причина отказа — типизированный вид вместо текста исключения:
     * экран подбирает локализованную подпись, слой данных остаётся
     * многоязычным. Текст исключения — только для «неизвестной» ошибки.
     */
    private fun failure(e: Exception): BackupResult.Failed = when (e) {
        is BackupImportException.NotABackup -> BackupResult.Failed.NotABackup

        // Формат архива и схема БД новее — одно и то же действие для
        // пользователя: обновить приложение и повторить.
        is BackupImportException.UnsupportedFormat,
        is BackupImportException.NewerSchema,
        -> BackupResult.Failed.NewerVersion

        // Нет базы, файл не SQLite, схема старше цепочки миграций —
        // копия не восстановится в этой версии, чем именно — неважно.
        is BackupImportException.NoDatabase,
        is BackupImportException.InvalidDatabase,
        is BackupImportException.UnsupportedSchema,
        -> BackupResult.Failed.Unreadable

        is BackupImportException.TooLarge -> BackupResult.Failed.TooLarge

        else -> BackupResult.Failed.Error(e.message)
    }

    /** Экран показал результат (тост/диалог) — убираем его из состояния. */
    fun backupResultShown() {
        backupStatus.value = BackupStatus()
    }

    // ── CalDAV-подписка: Яндекс-календари (этап 1 — только чтение) ──

    val caldavAccounts: StateFlow<List<CaldavAccount>> = observeCaldavAccountsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _caldavBusy = MutableStateFlow(false)
    val caldavBusy: StateFlow<Boolean> = _caldavBusy.asStateFlow()

    /** Итог последней синхронизации — для тоста; экран гасит через shown(). */
    private val _caldavResult = MutableStateFlow<CaldavSyncResult?>(null)
    val caldavResult: StateFlow<CaldavSyncResult?> = _caldavResult.asStateFlow()

    private val _caldavAddState = MutableStateFlow(CaldavAddState())
    val caldavAddState: StateFlow<CaldavAddState> = _caldavAddState.asStateFlow()

    fun caldavResultShown() {
        _caldavResult.value = null
    }

    fun openCaldavDialog() {
        // Один сторонний календарь — поддерживаемая конфигурация: при
        // подключённой подписке форма не открывается (кнопка скрыта,
        // здесь страховка от программных вызовов).
        if (caldavAccounts.value.isNotEmpty()) return
        _caldavAddState.value = CaldavAddState(open = true)
    }

    fun closeCaldavDialog() {
        _caldavAddState.value = CaldavAddState()
    }

    fun updateCaldavServer(value: String) {
        _caldavAddState.update { it.copy(server = value) }
    }

    fun updateCaldavUsername(value: String) {
        _caldavAddState.update { it.copy(username = value) }
    }

    fun updateCaldavPassword(value: String) {
        _caldavAddState.update { it.copy(password = value) }
    }

    fun selectCaldavCalendar(calendar: CaldavCalendarRef) {
        _caldavAddState.update { it.copy(selectedUrl = calendar.url) }
    }

    /** Шаг 1 формы: PROPFIND-поиск календарей с событиями на сервере. */
    fun discoverCaldavCalendars() {
        val add = _caldavAddState.value
        if (add.discovering || add.username.isBlank() || add.password.isBlank()) return
        viewModelScope.launch {
            _caldavAddState.update { it.copy(discovering = true, error = null) }
            try {
                val calendars = discoverCaldavCalendarsUseCase(
                    add.server.trim(),
                    add.username.trim(),
                    add.password,
                )
                _caldavAddState.update { state ->
                    if (calendars.isEmpty()) {
                        state.copy(discovering = false, error = CaldavErrorKind.NO_CALENDARS)
                    } else {
                        state.copy(
                            discovering = false,
                            calendars = calendars,
                            selectedUrl = calendars.first().url,
                        )
                    }
                }
            } catch (e: Exception) {
                _caldavAddState.update {
                    it.copy(discovering = false, error = toCaldavErrorKind(e), errorMessage = e.message)
                }
            }
        }
    }

    /** Шаг 2 формы: подключение выбранного календаря + первая синхронизация. */
    fun connectCaldavAccount() {
        val add = _caldavAddState.value
        val calendar = add.calendars.firstOrNull { it.url == add.selectedUrl } ?: return
        viewModelScope.launch {
            _caldavAddState.update { it.copy(connecting = true, error = null) }
            try {
                connectCaldavAccountUseCase(
                    add.server.trim(),
                    add.username.trim(),
                    add.password,
                    calendar,
                )
                // Форма отбрасывается целиком: аккаунт виден в списке.
                closeCaldavDialog()
            } catch (e: Exception) {
                _caldavAddState.update {
                    it.copy(connecting = false, error = toCaldavErrorKind(e), errorMessage = e.message)
                }
            }
        }
    }

    fun syncCaldavAccount(accountId: Long) {
        if (_caldavBusy.value) return
        viewModelScope.launch {
            _caldavBusy.value = true
            try {
                _caldavResult.value = syncCaldavAccountUseCase(accountId)
            } catch (_: CaldavSyncException) {
                // Причина — в lastError аккаунта, карточка покажет её сама.
            }
            _caldavBusy.value = false
        }
    }

    fun disconnectCaldavAccount(accountId: Long) {
        if (_caldavBusy.value) return
        viewModelScope.launch {
            _caldavBusy.value = true
            try {
                disconnectCaldavAccountUseCase(accountId)
            } catch (_: Exception) {
                // Отказ отключить маловероятен; карточка останется и так.
            }
            _caldavBusy.value = false
        }
    }

    private fun toCaldavErrorKind(e: Exception): CaldavErrorKind = when (e) {
        is CaldavSyncException.Network -> CaldavErrorKind.NETWORK
        is CaldavSyncException.Auth -> CaldavErrorKind.AUTH
        else -> CaldavErrorKind.OTHER
    }
}

/**
 * Состояние формы «Подключение календаря»: два шага (поля → найденные
 * календари) живут в одном состоянии, открытие сбрасывает всё до дефолтов.
 */
data class CaldavAddState(
    val open: Boolean = false,
    val server: String = CaldavCalendarRef.DEFAULT_SERVER,
    val username: String = "",
    val password: String = "",
    val calendars: List<CaldavCalendarRef> = emptyList(),
    val selectedUrl: String? = null,
    val discovering: Boolean = false,
    val connecting: Boolean = false,
    val error: CaldavErrorKind? = null,
    /** Текст исключения — для нестандартных сбоев ([CaldavErrorKind.OTHER]). */
    val errorMessage: String? = null,
)

/** Типизированный отказ формы подключения: экран подбирает подпись. */
enum class CaldavErrorKind {
    NETWORK,
    AUTH,
    NO_CALENDARS,
    OTHER,
}

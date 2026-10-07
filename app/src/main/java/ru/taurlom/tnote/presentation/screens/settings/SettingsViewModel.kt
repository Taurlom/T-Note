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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.ThemeKind
import ru.taurlom.tnote.domain.repository.BackupDescription
import ru.taurlom.tnote.domain.repository.BackupImportException
import ru.taurlom.tnote.domain.repository.BackupRepository
import ru.taurlom.tnote.domain.repository.SettingsRepository
import ru.taurlom.tnote.domain.usecase.ClearCalendarUseCase
import ru.taurlom.tnote.domain.usecase.SetReminderTimeUseCase
import ru.taurlom.tnote.domain.usecase.SetRemindersEnabledUseCase
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val setRemindersEnabledUseCase: SetRemindersEnabledUseCase,
    private val setReminderTimeUseCase: SetReminderTimeUseCase,
    private val clearCalendarUseCase: ClearCalendarUseCase,
    private val backupRepository: BackupRepository,
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
            selectedLanguage,
        ) { appearance, reminders, language ->
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
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    /** Тема применяется сразу, без кнопки «Применить»: она видна мгновенно. */
    fun applyTheme(theme: ThemeKind) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            settingsRepository.setSelectedFont(font)
        }
    }

    /** Порядок и видимость разделов применяются сразу, как тема. */
    fun updateSections(sections: List<String>) {
        viewModelScope.launch {
            settingsRepository.setVisibleSections(sections)
        }
    }

    /** Тумблер напоминаний: включение заводит цепочку, выключение гасит. */
    fun applyRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            setRemindersEnabledUseCase(enabled)
        }
    }

    /** Смена времени сводки: расписание перестраивается сразу. */
    fun applyReminderTime(minutes: Int) {
        viewModelScope.launch {
            setReminderTimeUseCase(minutes)
        }
    }

    fun clearCalendar() {
        viewModelScope.launch {
            clearCalendarUseCase()
        }
    }

    fun exportBackup(target: Uri) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
}

package com.example.timemanager.presentation.screens.settings

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.repository.BackupDescription
import com.example.timemanager.domain.repository.BackupImportException
import com.example.timemanager.domain.repository.BackupRepository
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.domain.usecase.ClearCalendarUseCase
import com.example.timemanager.domain.model.AppFont
import com.example.timemanager.domain.model.ThemeKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clearCalendarUseCase: ClearCalendarUseCase,
    private val backupRepository: BackupRepository
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

    fun openSectionsDialog() { _sectionsDialogOpen.value = true }
    fun closeSectionsDialog() { _sectionsDialogOpen.value = false }

    /** Метаданные выбранного файла копии — для диалога подтверждения. */
    private val _pendingImport = MutableStateFlow<BackupDescription?>(null)
    val pendingImport: StateFlow<BackupDescription?> = _pendingImport

    // Язык живёт в состоянии, а не вычисляется в combine: ViewModel
    // переживает пересоздание активности при смене локали, и без явного
    // обновления дропдаун показывал бы старое значение из кэша stateIn.
    private val selectedLanguage = MutableStateFlow(AppLanguage.current())

    private data class BackupStatus(
        val isBusy: Boolean = false,
        val result: BackupResult? = null
    )

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.selectedFont,
            settingsRepository.selectedTheme,
            settingsRepository.visibleSections,
            backupStatus,
            selectedLanguage
        ) { font, theme, sections, backup, language ->
            SettingsUiState(
                selectedFont = font,
                selectedTheme = theme,
                selectedLanguage = language,
                visibleSections = sections,
                sectionsLoaded = true,
                isBackupBusy = backup.isBusy,
                backupResult = backup.result
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
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
            }
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
                    missing = summary.missingPhotos.size
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
                    missing = summary.missingPhotos.size
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
        is BackupImportException.NewerSchema -> BackupResult.Failed.NewerVersion
        // Нет базы, файл не SQLite, схема старше цепочки миграций —
        // копия не восстановится в этой версии, чем именно — неважно.
        is BackupImportException.NoDatabase,
        is BackupImportException.InvalidDatabase,
        is BackupImportException.UnsupportedSchema -> BackupResult.Failed.Unreadable
        is BackupImportException.TooLarge -> BackupResult.Failed.TooLarge
        else -> BackupResult.Failed.Error(e.message)
    }

    /** Экран показал результат (тост/диалог) — убираем его из состояния. */
    fun backupResultShown() {
        backupStatus.value = BackupStatus()
    }
}

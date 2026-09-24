package com.example.timemanager.presentation.screens.settings

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.repository.BackupDescription
import com.example.timemanager.domain.repository.BackupRepository
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.domain.usecase.ClearCalendarUseCase
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.ThemeKind
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
            backupStatus,
            selectedLanguage
        ) { font, theme, backup, language ->
            SettingsUiState(
                selectedFont = font,
                selectedTheme = theme,
                selectedLanguage = language,
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
            val description = backupRepository.describeBackup(source)
            if (description == null) {
                backupStatus.value =
                    BackupStatus(result = BackupResult.Failed("не резервная копия T-Note"))
            } else {
                _pendingImport.value = description
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
            BackupStatus(result = BackupResult.Failed(e.message ?: "неизвестная ошибка"))
        }
    }

    /** Экран показал результат (тост/диалог) — убираем его из состояния. */
    fun backupResultShown() {
        backupStatus.value = BackupStatus()
    }
}

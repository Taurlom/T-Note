package com.example.timemanager.presentation.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private data class BackupStatus(
        val isBusy: Boolean = false,
        val result: BackupResult? = null
    )

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.selectedFont,
            settingsRepository.selectedTheme,
            backupStatus
        ) { font, theme, backup ->
            SettingsUiState(
                selectedFont = font,
                selectedTheme = theme,
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
            runBackup(BackupResult.Exported) { backupRepository.exportBackup(target) }
        }
    }

    fun importBackup(source: Uri) {
        viewModelScope.launch {
            runBackup(BackupResult.Imported) { backupRepository.importBackup(source) }
        }
    }

    private suspend fun runBackup(success: BackupResult, action: suspend () -> Unit) {
        backupStatus.value = BackupStatus(isBusy = true)
        backupStatus.value = try {
            action()
            BackupStatus(result = success)
        } catch (e: Exception) {
            BackupStatus(result = BackupResult.Failed(e.message ?: "неизвестная ошибка"))
        }
    }

    /** Экран показал результат (тост/диалог) — убираем его из состояния. */
    fun backupResultShown() {
        backupStatus.value = BackupStatus()
    }
}

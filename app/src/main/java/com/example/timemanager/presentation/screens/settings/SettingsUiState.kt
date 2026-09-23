package com.example.timemanager.presentation.screens.settings

import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.ThemeKind

/** Результат операции с резервной копией; читается экраном один раз и сбрасывается. */
sealed interface BackupResult {
    data object Exported : BackupResult
    data object Imported : BackupResult
    data class Failed(val message: String) : BackupResult
}

data class SettingsUiState(
    val selectedFont: AppFont = AppFont.PT_SANS,
    val selectedTheme: ThemeKind = ThemeKind.DARK,
    val isBackupBusy: Boolean = false,
    val backupResult: BackupResult? = null
)

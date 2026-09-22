package com.example.timemanager.presentation.screens.settings

import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.ThemeKind

data class SettingsUiState(
    val selectedFont: AppFont = AppFont.PT_SANS,
    val selectedTheme: ThemeKind = ThemeKind.DARK
)

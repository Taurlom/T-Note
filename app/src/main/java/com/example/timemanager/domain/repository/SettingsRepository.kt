package com.example.timemanager.domain.repository

import com.example.timemanager.presentation.theme.AppFont
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val selectedFont: Flow<AppFont>
    suspend fun setSelectedFont(font: AppFont)
}

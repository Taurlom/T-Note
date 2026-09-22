package com.example.timemanager.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.domain.usecase.ClearCalendarUseCase
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.ThemeKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clearCalendarUseCase: ClearCalendarUseCase
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.selectedFont, settingsRepository.selectedTheme) { font, theme ->
            SettingsUiState(selectedFont = font, selectedTheme = theme)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun applyFont(font: AppFont) {
        viewModelScope.launch {
            settingsRepository.setSelectedFont(font)
        }
    }

    /** Тема применяется сразу, без кнопки «Применить»: она видна мгновенно. */
    fun applyTheme(theme: ThemeKind) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun clearCalendar() {
        viewModelScope.launch {
            clearCalendarUseCase()
        }
    }
}

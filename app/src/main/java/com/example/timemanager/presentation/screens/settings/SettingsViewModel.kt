package com.example.timemanager.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.domain.usecase.ClearCalendarUseCase
import com.example.timemanager.presentation.theme.AppFont
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clearCalendarUseCase: ClearCalendarUseCase
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.selectedFont
        .map { font -> SettingsUiState(selectedFont = font) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

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
}

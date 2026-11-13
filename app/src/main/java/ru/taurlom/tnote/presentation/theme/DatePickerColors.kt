package ru.taurlom.tnote.presentation.theme

import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable

/**
 * Перевод ролей пикера даты ([AppColors]) в цвета Material 3:
 * `DatePicker(state, colors = appDatePickerColors())` — см. CalendarDayDialog.
 *
 * Аналогично [appTimePickerColors]: дефолты DatePicker берутся из ролей
 * colorScheme, которые наши темы сознательно не заполняют, поэтому пикер
 * красится ролями диалога.
 *
 *  - контейнер и заголовки — роли диалога;
 *  - выбранный день и год — акцент чипов/кнопок (в тёмной теме Gold
 *    с контентом Navy, как FAB);
 *  - «сегодня» — контур цвета контента вместо дефолтного primary.
 */
@OptIn(ExperimentalMaterial3Api::class) // DatePickerColors — экспериментальный M3 API
@Composable
internal fun appDatePickerColors(): DatePickerColors = DatePickerDefaults.colors(
    containerColor = AppTheme.colors.dialogContainer,
    titleContentColor = AppTheme.colors.dialogContent,
    headlineContentColor = AppTheme.colors.dialogContent,
    weekdayContentColor = AppTheme.colors.dialogContentMuted,
    subheadContentColor = AppTheme.colors.dialogContentMuted,
    navigationContentColor = AppTheme.colors.dialogContent,
    yearContentColor = AppTheme.colors.dialogContent,
    disabledYearContentColor = AppTheme.colors.dialogContentMuted,
    currentYearContentColor = AppTheme.colors.dialogContent,
    selectedYearContentColor = AppTheme.colors.chipSelectedContent,
    disabledSelectedYearContentColor = AppTheme.colors.dialogContentMuted,
    selectedYearContainerColor = AppTheme.colors.chipSelectedContainer,
    disabledSelectedYearContainerColor = AppTheme.colors.dialogContentMuted,
    dayContentColor = AppTheme.colors.dialogContent,
    disabledDayContentColor = AppTheme.colors.dialogContentMuted,
    selectedDayContentColor = AppTheme.colors.chipSelectedContent,
    disabledSelectedDayContentColor = AppTheme.colors.dialogContentMuted,
    selectedDayContainerColor = AppTheme.colors.chipSelectedContainer,
    disabledSelectedDayContainerColor = AppTheme.colors.dialogContentMuted,
    todayContentColor = AppTheme.colors.dialogContent,
    todayDateBorderColor = AppTheme.colors.dialogContent,
    dayInSelectionRangeContentColor = AppTheme.colors.dialogContent,
    dayInSelectionRangeContainerColor = AppTheme.colors.dialogContentMuted,
    dividerColor = AppTheme.colors.dialogContentMuted,
)

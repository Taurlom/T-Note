package ru.taurlom.tnote.presentation.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.Composable

/**
 * Перевод ролей пикера ([AppColors]) в цвета Material 3:
 * `TimePicker(state, colors = appTimePickerColors())`.
 *
 * Зачем отдельные роли, а не colorScheme: дефолты TimePicker в material3 1.3
 * берутся из ролей colorScheme (surfaceContainerHighest и др.), которые наши
 * темы сознательно не заполняют — весь остальной UI красится через
 * `AppTheme.colors`. Заполнять эти роли глобально нельзя точечно: они же
 * красят треки Switch'ей и другие M3-компоненты. Поэтому пикер настроен тем
 * же путём, что диалоги и поля, — своими ролями в каждой теме.
 *
 * Что здесь сознательно не переопределяется:
 *  - containerColor — в material3 1.3.1 пикер его не отрисовывает вовсе
 *    (роль была бы мёртвой); подложку под пикер рисует AppTimePicker своей
 *    Surface — см. роль AppColors.pickerBackdrop;
 *  - periodSelector* (AM/PM) — у нас is24Hour = true, переключатель скрыт;
 *  - двоеточие между «ЧЧ» и «ММ» не параметризовано в colors() вовсе —
 *    красится токеном onSurface; на тёмной подложке AppTimePicker оно
 *    читаемо во всех темах без отдельных ролей.
 */
@OptIn(ExperimentalMaterial3Api::class) // TimePickerColors — экспериментальный M3 API
@Composable
internal fun appTimePickerColors(): TimePickerColors = TimePickerDefaults.colors(
    clockDialColor = AppTheme.colors.pickerDial,
    clockDialUnselectedContentColor = AppTheme.colors.pickerDialContent,
    clockDialSelectedContentColor = AppTheme.colors.pickerSelectorContent,
    selectorColor = AppTheme.colors.pickerSelector,
    timeSelectorUnselectedContainerColor = AppTheme.colors.pickerDial,
    timeSelectorUnselectedContentColor = AppTheme.colors.pickerDialContent,
    timeSelectorSelectedContainerColor = AppTheme.colors.pickerChipSelectedContainer,
    timeSelectorSelectedContentColor = AppTheme.colors.pickerChipSelectedContent,
)

package com.example.timemanager.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Компонентные роли приложения — «что где красится».
 *
 * Стандартных ролей Material 3 (colorScheme) не хватает на места, специфичные
 * для UI: ячейка календаря, нижняя навигация, диалог. Каждое повторяющееся
 * место получает здесь свою переменную; конкретный цвет ей назначает тема
 * (см. Themes.kt).
 *
 * Доступ: `AppTheme.colors.dialogContent`.
 */
@Immutable
data class AppColors(
    // Верхняя панель раздела
    val appBarContainer: Color,
    val appBarContent: Color,
    /** Цвет панели, когда контент прокручен под ней (нужен scrollBehavior). */
    val appBarScrolledContainer: Color,
    // Диалоги и выпадающие списки
    val dialogContainer: Color,
    val dialogContent: Color,
    /** Приглушённый текст/иконки внутри диалога. */
    val dialogContentMuted: Color,
    // Текстовые поля (в диалогах)
    val fieldContent: Color,
    val fieldBorder: Color,
    val fieldBorderUnfocused: Color,
    // Текстовые поля на фоне экрана
    val fieldOnDarkContent: Color,
    val fieldOnDarkBorder: Color,
    /** Маркер обязательного поля («*») в лейбле. */
    val requiredMarker: Color,
    // Кнопки, чипы, плавающая кнопка
    val buttonContainer: Color,
    val buttonContent: Color,
    val buttonDisabledContainer: Color,
    val buttonDisabledContent: Color,
    val chipSelectedContainer: Color,
    val chipSelectedContent: Color,
    val chipContainer: Color,
    val chipContent: Color,
    val fabContainer: Color,
    val fabContent: Color,
    // Иконки действий на карточках (редактировать/удалить)
    val actionIcon: Color,
    // Нижняя навигация
    val bottomNavContainer: Color,
    val bottomNavIndicator: Color,
    val bottomNavActiveIcon: Color,
    val bottomNavInactiveIcon: Color,
    // Календарь
    val calendarCellContainer: Color,
    /** Фон выходных (Сб/Вс и дней, помеченных событием типа «Выходной»). */
    val calendarWeekendContainer: Color,
    val calendarTodayContainer: Color,
    val calendarDayNumber: Color,
    /** Число сегодняшнего дня — контрастно к [calendarTodayContainer]. */
    val calendarTodayNumber: Color,
    val calendarAdjacentDayNumber: Color,
    val calendarHeader: Color,
    val calendarWeekdayLabel: Color,
    val calendarEventMarker: Color,
    val calendarNoteMarker: Color,
    val calendarPastMarker: Color,
    // Заголовки секций настроек
    val sectionTitle: Color,
    // Шапка главного экрана: логотип и название приложения
    val brandLogo: Color,
    val brandTitle: Color,
    // Splash-экран (системный сплэш фиксирован тёмным — см. colors.xml)
    val launchBackground: Color
)

val LocalAppColors = staticCompositionLocalOf { DarkTheme.colors }

/** Точка доступа к компонентным ролям: `AppTheme.colors.dialogContent`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

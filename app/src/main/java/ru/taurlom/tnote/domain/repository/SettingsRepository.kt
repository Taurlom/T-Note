package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.ThemeKind

interface SettingsRepository {
    val selectedFont: Flow<AppFont>
    suspend fun setSelectedFont(font: AppFont)

    val selectedTheme: Flow<ThemeKind>
    suspend fun setSelectedTheme(theme: ThemeKind)

    /**
     * Видимые разделы в порядке нижней панели; скрытых в списке нет.
     * Пустой список — «значение не задано», порядок по умолчанию
     * (разбирается presentation-слоем, см. BottomNavItem.itemsFor).
     */
    val visibleSections: Flow<List<String>>
    suspend fun setVisibleSections(sections: List<String>)

    /**
     * Включена ли ежедневная сводка событий дня. По умолчанию — нет:
     * уведомления только по явному согласию пользователя, а не «все
     * получили по умолчанию и ищут, где выключить».
     */
    val remindersEnabled: Flow<Boolean>
    suspend fun setRemindersEnabled(enabled: Boolean)

    /**
     * Время сводки — минуты от полуночи (540 = 09:00). Хранится числом,
     * а не строкой: валидация и сравнение без парсинга.
     */
    val reminderTimeMinutes: Flow<Int>
    suspend fun setReminderTimeMinutes(minutes: Int)
}

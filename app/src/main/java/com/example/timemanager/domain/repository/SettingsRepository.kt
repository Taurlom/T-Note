package com.example.timemanager.domain.repository

import com.example.timemanager.domain.model.AppFont
import com.example.timemanager.domain.model.ThemeKind
import kotlinx.coroutines.flow.Flow

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
}

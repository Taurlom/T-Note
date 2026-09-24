package com.example.timemanager.presentation.theme

import androidx.annotation.StringRes
import com.example.timemanager.R

/**
 * Доступные цветовые темы приложения. Выбор хранится в настройках.
 * Название — строковый ресурс: переводится вместе с языком интерфейса.
 */
enum class ThemeKind(@StringRes val labelRes: Int) {
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    OCEAN(R.string.theme_ocean),
    FOREST(R.string.theme_forest),
    AUTUMN(R.string.theme_autumn);

    companion object {
        fun fromName(name: String): ThemeKind = entries.find { it.name == name } ?: DARK
    }
}

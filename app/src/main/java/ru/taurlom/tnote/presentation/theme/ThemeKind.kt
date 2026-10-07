package ru.taurlom.tnote.presentation.theme

import androidx.annotation.StringRes
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.ThemeKind

/**
 * Название темы — строковый ресурс: переводится вместе с языком интерфейса.
 * Сам enum живёт в domain (domain/model/ThemeKind.kt): его значение хранится
 * в настройках и в манифесте резервной копии.
 */
val ThemeKind.labelRes: Int
    @StringRes
    get() = when (this) {
        ThemeKind.DARK -> R.string.theme_dark
        ThemeKind.LIGHT -> R.string.theme_light
        ThemeKind.OCEAN -> R.string.theme_ocean
        ThemeKind.FOREST -> R.string.theme_forest
        ThemeKind.AUTUMN -> R.string.theme_autumn
    }

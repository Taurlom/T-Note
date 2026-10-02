package com.example.timemanager.domain.model

/**
 * Цветовые темы приложения. Выбор хранится в настройках (DataStore) и в
 * манифесте резервной копии по [name] — поэтому enum живёт в domain.
 * Подпись для UI (строковый ресурс) — extension `labelRes` в
 * presentation/theme/ThemeKind.kt; маппинг цветов — presentation/theme/Themes.kt.
 */
enum class ThemeKind {
    DARK,
    LIGHT,
    OCEAN,
    FOREST,
    AUTUMN;

    companion object {
        fun fromName(name: String): ThemeKind = entries.find { it.name == name } ?: DARK
    }
}
